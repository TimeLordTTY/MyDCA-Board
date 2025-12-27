# -*- coding: utf-8 -*-
"""
AdvisorService - 生产建议服务

为每个产品生成买入建议（BUY/HOLD/WAIT），不参与回测和自动下单。
"""
import logging
import json
from datetime import datetime, date, timedelta
from decimal import Decimal
from typing import Optional, Dict, Any, List

from data.db_connector import execute_query, execute_one
from data.product_service import get_product_by_id, get_products
from core.exchange_holdings_calculator import calculate_exchange_holdings
from core.pending_buy_service import get_pending_pool, add_pending_amount
from core.market_quote_service import get_latest_quote

from .strategy_interface import AdviceInput, AdviceOutput
from .strategies.percentile_advice import PercentileAdvice
from .repos.product_strategy_bind_repo import get_bind_by_product_id
from .repos.strategy_state_repo import get_state, save_state
from .repos.indicator_daily_repo import get_latest_indicator
from .repos.advisor_suggestion_repo import save_suggestion
from .invariants_check import check_invariants

logger = logging.getLogger(__name__)


def get_strategy_advice_instance(strategy_code: str):
    """根据策略代码获取策略实例"""
    if strategy_code == 'percentile':
        return PercentileAdvice()
    elif strategy_code == 'drawdown':
        from .strategies.drawdown_advice import DrawdownAdvice
        return DrawdownAdvice()
    elif strategy_code == 'profit_recycle':
        from .strategies.profit_recycle_advice import ProfitRecycleAdvice
        return ProfitRecycleAdvice()
    elif strategy_code == 'simple':
        from .strategies.simple_advice import SimpleAdvice
        return SimpleAdvice()
    else:
        raise ValueError(f"未知策略代码: {strategy_code}")


def get_budget_amount(product_id: int, from_account_id: Optional[int] = None, include_below_min: bool = False) -> Decimal:
    """
    获取今日预算金额
    
    优先级：
    1. task_dca（当日应买金额）
    2. account_pool_rules（从资金池账户按比例分配算出预算）
    
    注意：
    - 等待池（pending_buy_pool）的资金来源就是这些预算中被"扣留"的部分
    - 扣留原因：溢价刹车、预算不足最小成交额等
    
    Args:
        include_below_min: 如果为True，即使分配金额小于最小金额，也返回实际分配金额（用于UI显示）
    """
    today = date.today()
    
    # 优先从 task_dca 读取
    if from_account_id:
        sql = """
            SELECT planned_amount, executed_amount, pending_amount
            FROM task_dca
            WHERE task_date = %s 
              AND product_id = %s 
              AND from_account_id = %s
              AND status = 'PENDING'
        """
        task = execute_one(sql, (today, product_id, from_account_id))
        if task:
            # 返回计划金额（已执行+待买入）
            planned = Decimal(str(task.get('planned_amount', 0)))
            return planned
    
    # 如果没有 from_account_id，尝试查找所有账户的任务
    sql = """
        SELECT SUM(planned_amount) as total_amount
        FROM task_dca
        WHERE task_date = %s 
          AND product_id = %s
          AND status = 'PENDING'
    """
    task = execute_one(sql, (today, product_id))
    if task and task.get('total_amount'):
        return Decimal(str(task['total_amount']))
    
    # 如果没有 task_dca，尝试从 account_pool_rules 计算
    # 查找所有指向该产品的资金池规则
    sql = """
        SELECT apr.from_account_id, apr.ratio, apr.min_amount, apr.round_step,
               a.account_code, a.account_name
        FROM account_pool_rules apr
        INNER JOIN accounts a ON apr.from_account_id = a.id
        WHERE apr.to_product_id = %s 
          AND apr.is_active = 1
          AND a.is_active = 1
    """
    rules = execute_query(sql, (product_id,))
    
    if not rules:
        logger.debug(f"产品 {product_id} 未找到资金池规则")
        return Decimal('0')
    
    logger.info(f"产品 {product_id} 找到 {len(rules)} 条资金池规则")
    
    # 正确的逻辑：先每个账户乘以对应的比例，然后汇总
    # 1. 对每个账户：账户余额 × 该账户的分配比例
    from core.ledger_service import calc_account_balance
    
    allocated = Decimal('0')
    account_details = []
    
    for rule in rules:
        account_code = rule.get('account_code', '')
        account_name = rule.get('account_name', '')
        ratio = Decimal(str(rule.get('ratio', 0)))
        
        if not account_code:
            logger.warning(f"规则 from_account_id={rule['from_account_id']} 的账户代码为空，跳过")
            continue
        
        try:
            account_balance = calc_account_balance(account_code)
            # 每个账户的分配金额 = 账户余额 × 该账户的分配比例
            account_allocated = account_balance * ratio
            allocated += account_allocated
            
            account_details.append({
                'code': account_code,
                'name': account_name,
                'balance': account_balance,
                'ratio': ratio,
                'allocated': account_allocated
            })
            
            logger.debug(f"账户 {account_code} ({account_name}): 余额={account_balance:.2f}, 比例={ratio*100:.2f}%, 分配={account_allocated:.2f}")
        except Exception as e:
            logger.warning(f"计算账户余额失败: account_code={account_code}, account_name={account_name}, error={e}", exc_info=True)
            continue
    
    if not account_details:
        logger.warning(f"没有有效的账户数据，无法分配预算")
        return Decimal('0')
    
    logger.info(f"预算计算: 来自 {len(account_details)} 个账户，汇总后总分配金额={allocated:.2f}")
    
    # 2. 应用最小金额和取整（使用第一个规则的最小金额和取整粒度）
    first_rule = rules[0]
    min_amount = Decimal(str(first_rule.get('min_amount', 0)))
    round_step = Decimal(str(first_rule.get('round_step', 1)))
    
    if allocated < min_amount:
        if include_below_min:
            logger.debug(f"分配金额 {allocated:.2f} < 最小金额 {min_amount:.2f}，但include_below_min=True，返回实际金额")
            return allocated
        else:
            logger.debug(f"分配金额 {allocated:.2f} < 最小金额 {min_amount:.2f}，返回0")
            return Decimal('0')
    
    # 取整
    if round_step > 0:
        allocated_before_round = allocated
        allocated = (allocated / round_step).quantize(Decimal('1'), rounding='ROUND_DOWN') * round_step
        if allocated != allocated_before_round:
            logger.debug(f"取整: {allocated_before_round:.2f} -> {allocated:.2f} (粒度: {round_step})")
    
    logger.info(f"产品 {product_id} 最终预算: {allocated:.2f}")
    return allocated


def get_pending_amount_sum(product_id: int, from_account_id: Optional[int] = None) -> Decimal:
    """获取等待池累计金额"""
    if from_account_id:
        pool = get_pending_pool(product_id, from_account_id)
        if pool:
            return Decimal(str(pool.get('pending_amount', 0)))
    else:
        # 汇总所有账户的等待池
        sql = """
            SELECT SUM(pending_amount) as total_amount
            FROM pending_buy_pool
            WHERE product_id = %s AND pending_amount > 0
        """
        result = execute_one(sql, (product_id,))
        if result and result.get('total_amount'):
            return Decimal(str(result['total_amount']))
    return Decimal('0')


def get_strategy_config(strategy_code: str, param_set_id: str) -> Optional[Dict[str, Any]]:
    """获取策略配置参数"""
    sql = """
        SELECT param_json
        FROM strategy_config
        WHERE strategy_key = %s 
          AND param_set_id = %s
          AND is_active = 1
        LIMIT 1
    """
    row = execute_one(sql, (strategy_code, param_set_id))
    if row and row.get('param_json'):
        try:
            return json.loads(row['param_json'])
        except (json.JSONDecodeError, TypeError):
            logger.warning(f"解析param_json失败: strategy_code={strategy_code}, param_set_id={param_set_id}")
    return None


def get_yesterday_close(product_id: int) -> Optional[Decimal]:
    """获取昨日收盘价"""
    yesterday = date.today() - timedelta(days=1)
    sql = """
        SELECT close_price
        FROM market_bar_d
        WHERE product_id = %s AND trade_date = %s
        ORDER BY created_at DESC
        LIMIT 1
    """
    row = execute_one(sql, (product_id, yesterday))
    if row and row.get('close_price'):
        return Decimal(str(row['close_price']))
    return None


def run_for_product(product_id: int, as_of_time: Optional[datetime] = None) -> Optional[Dict[str, Any]]:
    """
    为单个产品生成建议
    
    Args:
        product_id: 产品ID
        as_of_time: 建议生成时间，None表示现在
        
    Returns:
        建议字典，如果失败返回None
    """
    if as_of_time is None:
        as_of_time = datetime.now()
    
    try:
        # 1. 读取产品信息
        product = get_product_by_id(product_id)
        if not product or not product.get('is_active'):
            logger.warning(f"产品不存在或未启用: product_id={product_id}")
            return None
        
        # 只处理场内产品
        if product.get('channel') != 'EXCHANGE':
            logger.debug(f"跳过非场内产品: product_id={product_id}, channel={product.get('channel')}")
            return None
        
        # 2. 读取策略绑定
        bind = get_bind_by_product_id(product_id)
        if not bind:
            # 未绑定策略，返回HOLD建议
            return {
                'product_id': product_id,
                'action': 'HOLD',
                'reason': '未绑定策略，请在产品管理页配置策略绑定',
                'as_of_time': as_of_time
            }
        
        strategy_code = bind['strategy_code']
        param_set_id = bind['param_set_id']
        
        # 3. 读取策略参数
        param_json = get_strategy_config(strategy_code, param_set_id)
        if not param_json:
            logger.warning(f"策略参数未找到: strategy_code={strategy_code}, param_set_id={param_set_id}")
            return {
                'product_id': product_id,
                'action': 'HOLD',
                'reason': f'策略参数未找到: {strategy_code}@{param_set_id}',
                'as_of_time': as_of_time
            }
        
        # 4. 读取策略状态
        state_row = get_state(product_id, strategy_code)
        state_json = None
        if state_row and state_row.get('state_json'):
            state_json = state_row['state_json']
        
        # 5. 读取指标（需要window_days）
        window_days = param_json.get('window_days', 750)
        # 使用昨天的日期查询指标（因为指标是基于昨天的收盘价计算的）
        yesterday = date.today() - timedelta(days=1)
        indicator = get_latest_indicator(product_id, window_days, max_date=yesterday)
        indicator_dict = None
        if indicator:
            indicator_dict = {
                'pct_rank': indicator.get('pct_rank'),
                'q_buy_price': indicator.get('q_buy_price'),
                'q_mid_price': indicator.get('q_mid_price'),
                'q_high_price': indicator.get('q_high_price'),
                'peak_close': indicator.get('peak_close'),
                'drawdown_from_peak': indicator.get('drawdown_from_peak'),
                'ma20': indicator.get('ma20'),
                'ma60': indicator.get('ma60')
            }
        else:
            logger.warning(f"指标未找到: product_id={product_id}, window_days={window_days}, max_date={yesterday}")
        
        # 6. 读取实时行情
        quote = get_latest_quote(product_id)
        if not quote:
            logger.warning(f"实时行情未找到: product_id={product_id}")
            return {
                'product_id': product_id,
                'action': 'WAIT',
                'reason': '实时行情未找到，请等待行情更新',
                'as_of_time': as_of_time
            }
        
        last_price = Decimal(str(quote.get('price', 0)))
        prev_close = Decimal(str(quote.get('prev_close', 0))) if quote.get('prev_close') else None
        premium_rate = Decimal(str(quote.get('premium_rate', 0))) if quote.get('premium_rate') else None
        
        # 如果没有prev_close，尝试从日K读取
        if not prev_close:
            prev_close = get_yesterday_close(product_id)
        
        if not prev_close:
            logger.warning(f"无法获取昨收价: product_id={product_id}")
            prev_close = last_price  # 降级：使用当前价
        
        # 7. 读取持仓
        holding = calculate_exchange_holdings(product_id)
        
        # 8. 计算预算
        # 尝试获取 from_account_id（优先级：task_dca > account_pool_rules）
        from_account_id = None
        today = date.today()
        
        # 优先从 task_dca 获取
        sql = """
            SELECT from_account_id
            FROM task_dca
            WHERE task_date = %s 
              AND product_id = %s
              AND status = 'PENDING'
            LIMIT 1
        """
        task = execute_one(sql, (today, product_id))
        if task and task.get('from_account_id'):
            from_account_id = task['from_account_id']
        else:
            # 如果没有 task_dca，从 account_pool_rules 获取第一个匹配的账户
            sql = """
                SELECT from_account_id
                FROM account_pool_rules
                WHERE to_product_id = %s 
                  AND is_active = 1
                LIMIT 1
            """
            rule = execute_one(sql, (product_id,))
            if rule and rule.get('from_account_id'):
                from_account_id = rule['from_account_id']
        
        # 获取实际预算（包括小于min_trade_amount的情况，用于策略判断）
        # 但策略内部仍需要检查min_trade_amount约束
        actual_budget = get_budget_amount(product_id, from_account_id, include_below_min=True)
        pending_amount = get_pending_amount_sum(product_id, from_account_id)
        
        logger.info(f"产品 {product_id} ({product.get('code', '')}) 预算计算: actual_budget={actual_budget:.2f}, pending_amount={pending_amount:.2f}, total={actual_budget + pending_amount:.2f}")
        
        # 9. 构建输入（传递实际预算，策略内部会检查min_trade_amount）
        input_data = AdviceInput(
            product_id=product_id,
            as_of_time=as_of_time,
            last_price=last_price,
            prev_close=prev_close,
            premium_rate=premium_rate,
            indicator=indicator_dict,
            holding=holding,
            budget_amount=actual_budget,
            pending_amount=pending_amount,
            bind_config=bind,
            param_json=param_json,
            state_json=state_json
        )
        
        # 10. 调用策略评估
        strategy_instance = get_strategy_advice_instance(strategy_code)
        output = strategy_instance.evaluate(input_data)
        
        # 11. 溢价刹车处理（全局统一处理）
        output = apply_premium_brake_to_output(output, product, premium_rate)
        
        # 12. 自检验收
        output = check_invariants(output, input_data, product)
        
        # 13. 保存建议
        suggestion_data = {
            'product_id': product_id,
            'as_of_time': as_of_time,
            'strategy_code': strategy_code,
            'action': output.action,
            'suggest_amount': float(output.suggest_amount),
            'suggest_ratio': float(output.suggest_ratio) if output.suggest_ratio else None,
            'limit_price_hint': float(output.limit_price_hint) if output.limit_price_hint else None,
            'premium_rate': float(output.premium_rate) if output.premium_rate else None,
            'moved_to_wait_pool': float(output.moved_to_wait_pool),
            'reason': output.reason
        }
        save_suggestion(suggestion_data)
        
        # 14. 更新等待池
        if output.moved_to_wait_pool > 0:
            if from_account_id:
                add_pending_amount(
                    product_id, from_account_id, 
                    float(output.moved_to_wait_pool),
                    reason="Advisor建议：预算不足或溢价刹车"
                )
        
        # 15. 更新策略状态
        if output.new_state_json:
            save_state(product_id, strategy_code, output.new_state_json)
        
        return {
            'product_id': product_id,
            'action': output.action,
            'suggest_amount': float(output.suggest_amount),
            'suggest_ratio': float(output.suggest_ratio) if output.suggest_ratio else None,
            'moved_to_wait_pool': float(output.moved_to_wait_pool),
            'reason': output.reason,
            'as_of_time': as_of_time
        }
        
    except Exception as e:
        logger.error(f"生成建议失败: product_id={product_id}, error={e}", exc_info=True)
        return None


def apply_premium_brake_to_output(output: AdviceOutput, product: Dict, premium_rate: Optional[Decimal]) -> AdviceOutput:
    """
    溢价刹车处理（全局统一处理）
    
    规则：
    - premium_rate <= 0.01：不改策略输出
    - 0.01 < premium_rate <= 0.02：半买
    - premium_rate > 0.02：强制WAIT
    """
    if not product.get('is_qdii'):
        # 非QDII产品，不需要溢价刹车
        return output
    
    if premium_rate is None:
        # 溢价率缺失，必须WAIT
        return AdviceOutput(
            action='WAIT',
            suggest_amount=Decimal('0'),
            suggest_ratio=None,
            limit_price_hint=output.limit_price_hint,
            premium_rate=None,
            moved_to_wait_pool=output.suggest_amount + output.moved_to_wait_pool,
            reason=f"{output.reason} 溢价率缺失，避免误买，全部进入等待池。",
            new_state_json=output.new_state_json
        )
    
    premium_float = float(premium_rate)
    
    if premium_float > 0.02:
        # 溢价>2%，强制WAIT
        return AdviceOutput(
            action='WAIT',
            suggest_amount=Decimal('0'),
            suggest_ratio=None,
            limit_price_hint=output.limit_price_hint,
            premium_rate=premium_rate,
            moved_to_wait_pool=output.suggest_amount + output.moved_to_wait_pool,
            reason=f"{output.reason} 当前溢价={premium_float*100:.2f}%，超过2%阈值，执行溢价刹车，全部进入等待池。建议窗口10:30-11:15/13:30-14:30；建议限价=昨收*0.998。",
            new_state_json=output.new_state_json
        )
    elif premium_float > 0.01:
        # 1%<溢价<=2%，半买
        if output.action == 'BUY':
            half_amount = output.suggest_amount / Decimal('2')
            return AdviceOutput(
                action=output.action,
                suggest_amount=half_amount,
                suggest_ratio=Decimal('0.5'),
                limit_price_hint=output.limit_price_hint,
                premium_rate=premium_rate,
                moved_to_wait_pool=half_amount + output.moved_to_wait_pool,
                reason=f"{output.reason} 当前溢价={premium_float*100:.2f}%，处于(1%,2%]，执行半买；建议窗口10:30-11:15/13:30-14:30；建议限价=昨收*0.998。",
                new_state_json=output.new_state_json
            )
        # 如果原本是HOLD，保持HOLD
        return output
    else:
        # 溢价<=1%，正常处理
        return output


def run_for_all_products(as_of_time: Optional[datetime] = None) -> Dict[str, Any]:
    """
    为所有场内产品生成建议
    
    Returns:
        {success_count, fail_count, results}
    """
    if as_of_time is None:
        as_of_time = datetime.now()
    
    products = get_products(channel='EXCHANGE', is_active=True)
    
    success_count = 0
    fail_count = 0
    results = []
    
    for product in products:
        product_id = product['id']
        result = run_for_product(product_id, as_of_time)
        if result:
            success_count += 1
            results.append(result)
        else:
            fail_count += 1
    
    logger.info(f"生成建议完成: 成功={success_count}, 失败={fail_count}")
    
    return {
        'success_count': success_count,
        'fail_count': fail_count,
        'results': results
    }

