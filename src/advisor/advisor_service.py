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
from .repos.product_strategy_bind_repo import get_bind_by_product_id, get_binds_by_product_id
from .repos.strategy_state_repo import get_state, save_state
from .repos.indicator_daily_repo import get_latest_indicator
from .repos.advisor_suggestion_repo import save_suggestion
from .invariants_check import check_invariants
from .strategy_composer import StrategyComposer
from .view_model import AdvisorViewModel, ReasonBlock
from utils.trade_calendar import is_trade_day, is_trade_time

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
    elif strategy_code == 'dca_4pct':
        from .strategies.dca_4pct_advice import Dca4PctAdvice
        return Dca4PctAdvice()
    else:
        raise ValueError(f"未知策略代码: {strategy_code}")


def get_budget_amount(product_id: int, from_account_id: Optional[int] = None, include_below_min: bool = False) -> Decimal:
    """
    获取今日预算金额
    
    来源：
    - account_pool_rules（从资金池账户按比例分配算出预算）
    
    注意：
    - 等待池（pending_buy_pool）的资金来源就是这些预算中被"扣留"的部分
    - 扣留原因：溢价刹车、预算不足最小成交额等
    
    Args:
        include_below_min: 如果为True，即使分配金额小于最小金额，也返回实际分配金额（用于UI显示）
    """
    # 从 account_pool_rules 计算
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
        
        # 2. 读取所有策略绑定（支持多策略组合）
        binds = get_binds_by_product_id(product_id)
        if not binds:
            # 未绑定策略，返回HOLD建议
            return {
                'product_id': product_id,
                'action': 'HOLD',
                'reason': '未绑定策略，请在产品管理页配置策略绑定',
                'as_of_time': as_of_time
            }
        
        # 获取第一个策略的配置（用于读取指标，使用最大window_days）
        first_bind = binds[0]
        strategy_codes = [b['strategy_code'] for b in binds]
        
        # 3. 为所有策略读取参数配置（用于StrategyComposer）
        # 注意：StrategyComposer内部会为每个策略单独读取param_json
        # 这里先读取第一个策略的window_days用于指标查询
        first_param_json = get_strategy_config(first_bind['strategy_code'], first_bind['param_set_id'])
        if not first_param_json:
            logger.warning(f"第一个策略参数未找到: strategy_code={first_bind['strategy_code']}, param_set_id={first_bind['param_set_id']}")
            return {
                'product_id': product_id,
                'action': 'HOLD',
                'reason': f'策略参数未找到: {first_bind["strategy_code"]}@{first_bind["param_set_id"]}',
                'as_of_time': as_of_time
            }
        
        # 4. 读取策略状态（为所有策略）
        # 注意：StrategyComposer内部会为每个策略单独读取state_json
        
        # 5. 读取指标（使用最大window_days）
        window_days = first_param_json.get('window_days', 750)
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
        # 从 account_pool_rules 获取第一个匹配的账户（用于等待池查询）
        from_account_id = None
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
        
        # 8.1 明确计算三个金额概念
        # new_budget: 本轮新增预算（根据资金规则计算出的"新可投入金额"）
        new_budget = get_budget_amount(product_id, from_account_id, include_below_min=True)
        
        # wait_pool_before: 等待池余额（历史累计，before本次处理）
        wait_pool_before = get_pending_amount_sum(product_id, from_account_id)
        
        # planned_amount: 本轮可用于买入（=new_budget + wait_pool_before）
        planned_amount = new_budget + wait_pool_before
        
        logger.info(f"产品 {product_id} ({product.get('code', '')}) 预算计算: new_budget={new_budget:.2f}, wait_pool_before={wait_pool_before:.2f}, planned_amount={planned_amount:.2f}")
        
        # 9. 计算可用现金池余额（从所有相关账户汇总）
        from core.ledger_service import calc_account_balance
        cash_available = Decimal('0')
        sql_cash = """
            SELECT DISTINCT apr.from_account_id, a.account_code
            FROM account_pool_rules apr
            INNER JOIN accounts a ON apr.from_account_id = a.id
            WHERE apr.to_product_id = %s 
              AND apr.is_active = 1
              AND a.is_active = 1
        """
        cash_rules = execute_query(sql_cash, (product_id,))
        for rule in cash_rules:
            account_code = rule.get('account_code', '')
            if account_code:
                try:
                    balance = calc_account_balance(account_code)
                    cash_available += Decimal(str(balance))
                except Exception as e:
                    logger.warning(f"计算账户余额失败: account_code={account_code}, error={e}")
        
        # 10. 计算可用执行预算
        # budget_for_execution = min(planned_amount, cash_available)
        budget_for_execution = min(planned_amount, cash_available)
        
        # 兼容字段：plan_budget_today = new_budget
        plan_budget_today = new_budget
        
        # 11. 构建统一的输入（用于StrategyComposer）
        # 注意：StrategyComposer内部会为每个策略单独构建AdviceInput
        # 这里先构建一个基础输入，包含所有策略共用的数据
        # 策略输入使用 planned_amount（包含新预算和等待池）
        base_input_data = AdviceInput(
            product_id=product_id,
            as_of_time=as_of_time,
            last_price=last_price,
            prev_close=prev_close,
            premium_rate=premium_rate,
            indicator=indicator_dict,
            holding=holding,
            budget_amount=new_budget,  # 新预算
            pending_amount=wait_pool_before,  # 等待池余额
            bind_config=first_bind,  # 临时使用第一个bind_config
            param_json=first_param_json,  # 临时使用第一个param_json
            state_json=None  # StrategyComposer内部会读取
        )
        
        # 12. 调用策略组合器生成建议
        # StrategyComposer内部会为每个策略读取param_json和state_json
        output = StrategyComposer.compose(binds, base_input_data, product)
        
        # 13. 判断交易日和交易时段（提前判断，用于后续逻辑）
        trade_day = is_trade_day(as_of_time.date() if isinstance(as_of_time, datetime) else as_of_time)
        trade_time = is_trade_time(as_of_time) if isinstance(as_of_time, datetime) else False
        
        # 14. 溢价刹车处理（全局统一处理）
        output = apply_premium_brake_to_output(output, product, premium_rate)
        
        # 15. 非交易日处理：非交易日时，不执行买入，也不进入等待池（预算冻结）
        # 关键：非交易日 moved_to_wait_pool=0，预算冻结，下一交易日开盘前重新评估
        if not trade_day:
            output = AdviceOutput(
                action='WAIT',
                suggest_amount=Decimal('0'),
                suggest_ratio=None,
                limit_price_hint=output.limit_price_hint,
                premium_rate=output.premium_rate,
                moved_to_wait_pool=Decimal('0'),  # 非交易日不入等待池
                reason=f"{output.reason} 市场关闭：预算冻结，下一交易日开盘前重新评估。",
                new_state_json=output.new_state_json
            )
        
        # 16. 计算最终执行金额和等待池金额
        # 关键：等待池增加规则（只在有买入意图时）
        # 只有当 planned_amount > 0 且策略触发买入意图时，才进入等待池
        budget_to_execute = output.suggest_amount
        budget_to_wait_pool = output.moved_to_wait_pool
        
        # 如果策略未触发（如分位太高，action=HOLD且suggest_amount=0），且planned_amount=0，则不进入等待池
        if planned_amount == 0:
            # 没有买入意图，不进入等待池
            budget_to_wait_pool = Decimal('0')
            budget_to_execute = Decimal('0')
        elif output.action != 'BUY' and output.suggest_amount == 0 and budget_to_wait_pool == 0:
            # 策略未触发买入，且没有其他原因进入等待池，则不进入等待池
            budget_to_wait_pool = Decimal('0')
        
        # 溢价刹车特殊处理：如果溢价>2%，全部预算进入等待池
        if premium_rate and float(premium_rate) > 0.02:
            budget_to_execute = Decimal('0')
            budget_to_wait_pool = planned_amount  # 使用planned_amount
        # 如果溢价1%-2%，半买，另一半进入等待池
        elif premium_rate and 0.01 < float(premium_rate) <= 0.02:
            if output.action == 'BUY':
                # 半买逻辑已在apply_premium_brake_to_output中处理
                # 但需要确保budget_to_wait_pool不超过planned_amount
                if budget_to_execute + budget_to_wait_pool > planned_amount:
                    # 按比例缩减
                    total = budget_to_execute + budget_to_wait_pool
                    if total > 0:
                        budget_to_execute = planned_amount * (budget_to_execute / total)
                        budget_to_wait_pool = planned_amount * (budget_to_wait_pool / total)
                    else:
                        budget_to_execute = Decimal('0')
                        budget_to_wait_pool = planned_amount
            else:
                # 如果原本不是BUY，保持原样
                if budget_to_execute + budget_to_wait_pool > planned_amount:
                    budget_to_wait_pool = planned_amount - budget_to_execute
        else:
            # 正常情况：确保不超过planned_amount
            if budget_to_execute + budget_to_wait_pool > planned_amount:
                # 按比例缩减
                total = budget_to_execute + budget_to_wait_pool
                if total > 0:
                    budget_to_execute = planned_amount * (budget_to_execute / total)
                    budget_to_wait_pool = planned_amount * (budget_to_wait_pool / total)
                else:
                    budget_to_execute = Decimal('0')
                    budget_to_wait_pool = planned_amount
        
        # 确保：实际执行 + 转等待池 <= planned_amount
        # 同时确保不超过budget_for_execution（现金限制）
        if budget_to_execute + budget_to_wait_pool > budget_for_execution:
            # 受现金限制，按比例缩减
            total = budget_to_execute + budget_to_wait_pool
            if total > 0:
                budget_to_execute = budget_for_execution * (budget_to_execute / total)
                budget_to_wait_pool = budget_for_execution * (budget_to_wait_pool / total)
            else:
                budget_to_execute = Decimal('0')
                budget_to_wait_pool = budget_for_execution
        
        # 17. 计算比例
        execute_ratio = (budget_to_execute / budget_for_execution) if budget_for_execution > 0 else Decimal('0')
        wait_ratio = (budget_to_wait_pool / budget_for_execution) if budget_for_execution > 0 else Decimal('0')
        
        # 18. 构建AdvisorViewModel
        
        # 计算涨跌幅
        pct_change = None
        if prev_close and prev_close > 0:
            pct_change = (last_price - prev_close) / prev_close
        
        # 计算价格相对MA
        price_over_ma20 = None
        price_over_ma60 = None
        if indicator_dict:
            ma20 = indicator_dict.get('ma20')
            ma60 = indicator_dict.get('ma60')
            if ma20:
                price_over_ma20 = last_price > Decimal(str(ma20))
            if ma60:
                price_over_ma60 = last_price > Decimal(str(ma60))
        
        # 计算手续费和一手约束
        fee_rate = Decimal(str(first_bind.get('fee_rate', 0.000845)))
        fee_min = Decimal(str(first_bind.get('fee_min', 0.20)))
        estimated_fee = max(budget_to_execute * fee_rate, fee_min) if budget_to_execute > 0 else Decimal('0')
        
        # 判断是否为ETF/LOF（需要一手约束）
        lot_size = None
        suggest_shares = None
        rounded_amount = None
        if product.get('market') in ['SH', 'SZ'] and product.get('product_type') in ['ETF', 'LOF']:
            lot_size = 100
            if last_price > 0:
                suggest_shares = int(budget_to_execute / last_price)
                rounded_shares = (suggest_shares // 100) * 100
                rounded_amount = Decimal(str(rounded_shares)) * last_price
        
        # 构建结构化的reason_blocks（包含5个区块）
        reason_blocks = []
        
        # 1) 本轮预算
        reason_blocks.append(ReasonBlock(
            rule_name="本轮预算",
            input_values={
                "new_budget": float(new_budget),
                "wait_pool_before": float(wait_pool_before),
                "planned_amount": float(planned_amount)
            },
            decision=f"新增预算{new_budget:.2f} + 待买入池{wait_pool_before:.2f} = 可用上限{planned_amount:.2f}"
        ))
        
        # 2) 市场条件
        market_conditions = {}
        if indicator_dict:
            if indicator_dict.get('pct_rank') is not None:
                market_conditions['pct_rank'] = float(indicator_dict['pct_rank']) * 100
            if indicator_dict.get('drawdown_from_peak') is not None:
                market_conditions['drawdown_from_peak'] = float(indicator_dict['drawdown_from_peak']) * 100
            if indicator_dict.get('ma20'):
                market_conditions['ma20'] = float(indicator_dict['ma20'])
            if indicator_dict.get('ma60'):
                market_conditions['ma60'] = float(indicator_dict['ma60'])
        if premium_rate is not None:
            market_conditions['premium_rate'] = float(premium_rate) * 100
        
        market_decision = ", ".join([f"{k}={v:.2f}{'%' if k in ['pct_rank', 'drawdown_from_peak', 'premium_rate'] else ''}" for k, v in market_conditions.items()])
        reason_blocks.append(ReasonBlock(
            rule_name="市场条件",
            input_values=market_conditions,
            decision=market_decision if market_decision else "无关键指标"
        ))
        
        # 3) 否决/刹车命中
        veto_hits = []
        veto_inputs = {}
        if not trade_day:
            veto_hits.append("非交易日（时间veto）")
            veto_inputs['is_trade_day'] = False
        if premium_rate and float(premium_rate) > 0.02:
            veto_hits.append("溢价>2%（溢价刹车）")
            veto_inputs['premium_rate'] = float(premium_rate) * 100
        elif premium_rate and 0.01 < float(premium_rate) <= 0.02:
            veto_hits.append("溢价1%-2%（半买）")
            veto_inputs['premium_rate'] = float(premium_rate) * 100
        if budget_to_execute > 0 and budget_to_execute < min(Decimal(str(first_bind.get('min_trade_amount', 1000))), budget_for_execution):
            veto_hits.append("最小成交额不足（门槛veto）")
            veto_inputs['min_trade_amount'] = float(first_bind.get('min_trade_amount', 1000))
            veto_inputs['budget_to_execute'] = float(budget_to_execute)
        if budget_to_execute > cash_available:
            veto_hits.append("现金不足（资金veto）")
            veto_inputs['cash_available'] = float(cash_available)
            veto_inputs['budget_to_execute'] = float(budget_to_execute)
        
        reason_blocks.append(ReasonBlock(
            rule_name="否决/刹车命中",
            input_values=veto_inputs,
            decision=", ".join(veto_hits) if veto_hits else "无"
        ))
        
        # 4) 执行与延期
        reason_blocks.append(ReasonBlock(
            rule_name="执行与延期",
            input_values={
                "budget_to_execute": float(budget_to_execute),
                "budget_to_wait_pool": float(budget_to_wait_pool),
                "estimated_fee": float(estimated_fee)
            },
            decision=f"建议买入={budget_to_execute:.2f}，进入等待池={budget_to_wait_pool:.2f}，预计手续费={estimated_fee:.2f}"
        ))
        
        # 5) 明日如何变化
        reason_blocks.append(ReasonBlock(
            rule_name="明日如何变化",
            input_values={},
            decision="待买入池会保留到下一交易日，后续满足条件时优先用于买入；新增预算按计划日重新计算。"
        ))
        
        # 计算等待池 after（Advisor不扣减，所以after = before + moved_to_wait）
        wait_pool_after = wait_pool_before + budget_to_wait_pool
        
        view_model = AdvisorViewModel(
            is_trade_day=trade_day,
            is_trade_time=trade_time,
            quote_time=quote.get('quote_time') if quote else None,
            last_price=last_price,
            prev_close=prev_close,
            pct_change=pct_change,
            iopv=Decimal(str(quote.get('iopv', 0))) if quote and quote.get('iopv') else None,
            premium_rate=premium_rate,
            pct_rank=Decimal(str(indicator_dict['pct_rank'])) if indicator_dict and indicator_dict.get('pct_rank') else None,
            peak_close=Decimal(str(indicator_dict['peak_close'])) if indicator_dict and indicator_dict.get('peak_close') else None,
            drawdown_from_peak=Decimal(str(indicator_dict['drawdown_from_peak'])) if indicator_dict and indicator_dict.get('drawdown_from_peak') else None,
            ma20=Decimal(str(indicator_dict['ma20'])) if indicator_dict and indicator_dict.get('ma20') else None,
            ma60=Decimal(str(indicator_dict['ma60'])) if indicator_dict and indicator_dict.get('ma60') else None,
            price_over_ma20=price_over_ma20,
            price_over_ma60=price_over_ma60,
            cash_available=cash_available,
            wait_pool_balance=wait_pool_after,  # 显示after（用于UI）
            new_budget=new_budget,
            wait_pool_before=wait_pool_before,
            planned_amount=planned_amount,
            plan_budget_today=plan_budget_today,  # 兼容字段
            budget_for_execution=budget_for_execution,
            budget_to_wait_pool=budget_to_wait_pool,
            budget_to_execute=budget_to_execute,
            fee_rate=fee_rate,
            fee_min=fee_min,
            min_trade_amount=Decimal(str(first_bind.get('min_trade_amount', 1000))),
            ideal_trade_amount=Decimal(str(first_bind.get('ideal_trade_amount', 2000))),
            estimated_fee=estimated_fee,
            lot_size=lot_size,
            suggest_shares=suggest_shares,
            rounded_amount=rounded_amount,
            action=output.action,
            execute_ratio=execute_ratio,
            wait_ratio=wait_ratio,
            limit_price_hint=output.limit_price_hint,
            time_window_hint="10:30-11:15/13:30-14:30" if product.get('is_qdii') else None,
            reason_blocks=reason_blocks,
            strategy_codes=strategy_codes
        )
        
        # 19. 自检验收（使用ViewModel）
        validation_errors = view_model.validate()
        if validation_errors:
            logger.warning(f"ViewModel验证失败: {validation_errors}")
            # 降级为WAIT
            view_model.action = 'WAIT'
            view_model.budget_to_execute = Decimal('0')
            view_model.budget_to_wait_pool = budget_for_execution
            view_model.execute_ratio = Decimal('0')
            view_model.wait_ratio = Decimal('1') if budget_for_execution > 0 else Decimal('0')
            view_model.reason_blocks.append(ReasonBlock(
                rule_name="自检验收",
                input_values={"errors": validation_errors},
                decision="降级为WAIT"
            ))
        
        # 20. 更新output以匹配ViewModel
        output.suggest_amount = view_model.budget_to_execute
        output.moved_to_wait_pool = view_model.budget_to_wait_pool
        
        # 21. 保存建议（包含扩展字段）
        suggestion_data = {
            'product_id': product_id,
            'as_of_time': as_of_time,
            'strategy_code': strategy_codes[0] if strategy_codes else '',  # 主策略代码
            'action': view_model.action,
            'suggest_amount': float(view_model.budget_to_execute),  # 兼容字段
            'suggest_ratio': float(view_model.execute_ratio),  # 兼容字段
            'limit_price_hint': float(view_model.limit_price_hint) if view_model.limit_price_hint else None,
            'premium_rate': float(view_model.premium_rate) if view_model.premium_rate else None,
            'moved_to_wait_pool': float(view_model.budget_to_wait_pool),  # 兼容字段
            'reason': output.reason,
            'cash_available': float(view_model.cash_available),
            'wait_pool_balance': float(view_model.wait_pool_balance),  # after
            'new_budget': float(view_model.new_budget),
            'wait_pool_before': float(view_model.wait_pool_before),
            'planned_amount': float(view_model.planned_amount),
            'plan_budget_today': float(view_model.plan_budget_today),  # 兼容字段
            'budget_for_execution': float(view_model.budget_for_execution),
            'budget_to_execute': float(view_model.budget_to_execute),
            'budget_to_wait_pool': float(view_model.budget_to_wait_pool),
            'execute_ratio': float(view_model.execute_ratio),
            'wait_ratio': float(view_model.wait_ratio),
            'reason_blocks': [{"rule_name": b.rule_name, "input_values": b.input_values, "decision": b.decision} for b in view_model.reason_blocks]
        }
        save_suggestion(suggestion_data)
        
        # 21.1 保存 budget_trace（审计日志）
        try:
            from repos.budget_trace_repo import save_budget_trace
            
            # 构建结构化的reason_text（包含5个区块）
            reason_text_parts = []
            
            # 1) 本轮预算
            reason_text_parts.append(f"【本轮预算】新增预算{new_budget:.2f} + 待买入池{wait_pool_before:.2f} = 可用上限{planned_amount:.2f}")
            
            # 2) 市场条件
            market_conditions = []
            if indicator_dict:
                if indicator_dict.get('pct_rank') is not None:
                    market_conditions.append(f"分位排名={float(indicator_dict['pct_rank'])*100:.1f}%")
                if indicator_dict.get('drawdown_from_peak') is not None:
                    market_conditions.append(f"回撤幅度={float(indicator_dict['drawdown_from_peak'])*100:.2f}%")
                if indicator_dict.get('ma20'):
                    market_conditions.append(f"MA20={float(indicator_dict['ma20']):.4f}")
                if indicator_dict.get('ma60'):
                    market_conditions.append(f"MA60={float(indicator_dict['ma60']):.4f}")
            if premium_rate is not None:
                market_conditions.append(f"溢价率={float(premium_rate)*100:.2f}%")
            if market_conditions:
                reason_text_parts.append(f"【市场条件】{', '.join(market_conditions)}")
            
            # 3) 否决/刹车命中
            veto_hits = []
            if not trade_day:
                veto_hits.append("非交易日（时间veto）")
            if premium_rate and float(premium_rate) > 0.02:
                veto_hits.append("溢价>2%（溢价刹车）")
            elif premium_rate and 0.01 < float(premium_rate) <= 0.02:
                veto_hits.append("溢价1%-2%（半买）")
            if budget_to_execute > 0 and budget_to_execute < min(Decimal(str(first_bind.get('min_trade_amount', 1000))), budget_for_execution):
                veto_hits.append("最小成交额不足（门槛veto）")
            if budget_to_execute > cash_available:
                veto_hits.append("现金不足（资金veto）")
            if veto_hits:
                reason_text_parts.append(f"【否决/刹车命中】{', '.join(veto_hits)}")
            else:
                reason_text_parts.append("【否决/刹车命中】无")
            
            # 4) 执行与延期
            reason_text_parts.append(f"【执行与延期】建议买入={budget_to_execute:.2f}，进入等待池={budget_to_wait_pool:.2f}，预计手续费={estimated_fee:.2f}")
            
            # 5) 明日如何变化
            reason_text_parts.append("【明日如何变化】待买入池会保留到下一交易日，后续满足条件时优先用于买入；新增预算按计划日重新计算。")
            
            reason_text = "\n".join(reason_text_parts)
            
            # 确定reason_code
            if not trade_day:
                reason_code = "NON_TRADE_DAY"
            elif premium_rate and float(premium_rate) > 0.02:
                reason_code = "PREMIUM_BRAKE"
            elif premium_rate and 0.01 < float(premium_rate) <= 0.02:
                reason_code = "PREMIUM_BRAKE_HALF"
            elif budget_to_execute > 0 and budget_to_execute < min(Decimal(str(first_bind.get('min_trade_amount', 1000))), budget_for_execution):
                reason_code = "MIN_TRADE_LIMIT"
            elif budget_to_execute > cash_available:
                reason_code = "INSUFFICIENT_CASH"
            elif output.action == 'BUY':
                reason_code = "BUY_EXECUTED"
            elif output.action == 'HOLD':
                reason_code = "HOLD_NO_TRIGGER"
            else:
                reason_code = "WAIT_OTHER"
            
            trace_data = {
                'product_id': product_id,
                'as_of_time': as_of_time,
                'new_budget': new_budget,
                'wait_pool_before': wait_pool_before,
                'planned_amount': planned_amount,
                'executed_amount': budget_to_execute,
                'moved_to_wait': budget_to_wait_pool,
                'wait_pool_after': wait_pool_after,
                'reason_code': reason_code,
                'reason_text': reason_text
            }
            save_budget_trace(trace_data)
            logger.debug(f"保存预算追踪: product_id={product_id}, reason_code={reason_code}")
        except Exception as e:
            logger.warning(f"保存预算追踪失败: {e}", exc_info=True)
            # 不阻断主流程
        
        # 22. 更新等待池（避免重复累加）
        # 关键：检查上次建议的budget_to_wait_pool，只增加增量部分
        if view_model.budget_to_wait_pool > 0:
            if from_account_id:
                # 获取上次建议的budget_to_wait_pool
                from repos.advisor_suggestion_repo import get_latest_suggestion
                last_suggestion = get_latest_suggestion(product_id)
                last_moved_to_wait = Decimal('0')
                if last_suggestion:
                    last_moved_to_wait = Decimal(str(last_suggestion.get('budget_to_wait_pool') or last_suggestion.get('moved_to_wait_pool') or 0))
                
                # 计算增量（本次 - 上次）
                increment = view_model.budget_to_wait_pool - last_moved_to_wait
                
                if increment > 0:
                    # 确定变更原因代码
                    if not trade_day:
                        change_reason = "NON_TRADE_DAY"
                        wait_reason = "Advisor建议：非交易日"
                    elif premium_rate and float(premium_rate) > 0.02:
                        change_reason = "PREMIUM_BRAKE"
                        wait_reason = "Advisor建议：溢价刹车（>2%）"
                    elif premium_rate and 0.01 < float(premium_rate) <= 0.02:
                        change_reason = "PREMIUM_BRAKE"
                        wait_reason = "Advisor建议：溢价刹车（1%-2%）"
                    elif budget_to_execute < min(Decimal(str(first_bind.get('min_trade_amount', 1000))), budget_for_execution):
                        change_reason = "MIN_TRADE_LIMIT"
                        wait_reason = "Advisor建议：最小成交额不足"
                    else:
                        change_reason = "OTHER"
                        wait_reason = "Advisor建议：预算不足或其他原因"
                    
                    add_pending_amount(
                        product_id, from_account_id, 
                        float(increment),
                        reason=wait_reason,
                        last_change_reason=change_reason
                    )
                    logger.info(f"等待池增量更新: product_id={product_id}, increment={increment:.2f}, reason={change_reason}")
                elif increment < 0:
                    # 如果增量是负数，说明等待池应该减少（不应该发生，但记录日志）
                    logger.warning(f"等待池增量为负: product_id={product_id}, increment={increment:.2f}, 跳过更新")
                else:
                    logger.debug(f"等待池无变化: product_id={product_id}, budget_to_wait_pool={view_model.budget_to_wait_pool:.2f}")
        
        # 23. 更新策略状态（为所有策略）
        if output.new_state_json:
            # 更新第一个策略的状态（兼容旧逻辑）
            save_state(product_id, strategy_codes[0], output.new_state_json)
        
        return {
            'product_id': product_id,
            'action': view_model.action,
            'suggest_amount': float(view_model.budget_to_execute),
            'suggest_ratio': float(view_model.execute_ratio),
            'moved_to_wait_pool': float(view_model.budget_to_wait_pool),
            'reason': output.reason,
            'as_of_time': as_of_time,
            'view_model': view_model  # 返回完整的ViewModel
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

