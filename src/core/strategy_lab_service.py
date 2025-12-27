# -*- coding: utf-8 -*-
"""
策略实验室服务 - 提供回测结果查询和运行接口
"""

from datetime import date, datetime
from typing import List, Dict, Optional, Any
from decimal import Decimal
import json
import logging

from data.db_connector import execute_query, execute_one
from data.product_service import get_product_by_id, get_products
# 导入策略模块以确保策略类被注册
import strategy_lab.strategy  # noqa: F401
from strategy_lab.framework.registry import list_strategies, get_strategy, get_strategy_info
from strategy_lab.data.provider import DataProvider
from strategy_lab.account.cash_model import CashModel
from strategy_lab.runner.backtester import Backtester
from strategy_lab.runner.param_runner import ParamRunner

logger = logging.getLogger(__name__)


def list_backtest_summaries(
    product_id: Optional[int] = None,
    strategy_key: Optional[str] = None,
    limit: int = 100
) -> List[Dict]:
    """
    查询回测汇总列表
    
    Args:
        product_id: 产品ID（可选）
        strategy_key: 策略标识（可选）
        limit: 返回条数限制
    
    Returns:
        回测汇总列表
    """
    sql = """
        SELECT 
            bs.id, bs.product_id, bs.strategy_key, bs.strategy_version,
            bs.param_set_id, bs.start_date, bs.end_date,
            bs.initial_cash, bs.final_value, bs.total_return,
            bs.annual_return, bs.max_drawdown, bs.trade_count,
            bs.total_fees, bs.fee_ratio, bs.wait_pool_ratio,
            bs.created_at,
            p.code as product_code, p.product_name,
            DATEDIFF(bs.end_date, bs.start_date) as days_diff,
            sc.param_json
        FROM backtest_summary bs
        LEFT JOIN products p ON bs.product_id = p.id
        LEFT JOIN strategy_config sc ON bs.strategy_key = sc.strategy_key 
            AND bs.strategy_version = sc.strategy_version 
            AND bs.param_set_id = sc.param_set_id
        WHERE 1=1
    """
    params = []
    
    if product_id:
        sql += " AND bs.product_id = %s"
        params.append(product_id)
    
    if strategy_key:
        sql += " AND bs.strategy_key = %s"
        params.append(strategy_key)
    
    sql += " ORDER BY bs.created_at DESC LIMIT %s"
    params.append(limit)
    
    return execute_query(sql, tuple(params))


def get_backtest_summary(summary_id: int) -> Optional[Dict]:
    """
    获取单个回测汇总
    
    Args:
        summary_id: 汇总ID
    
    Returns:
        回测汇总信息
    """
    sql = """
        SELECT 
            bs.*,
            p.code as product_code, p.product_name
        FROM backtest_summary bs
        LEFT JOIN products p ON bs.product_id = p.id
        WHERE bs.id = %s
    """
    return execute_one(sql, (summary_id,))


def get_backtest_daily_records(summary_id: int) -> List[Dict]:
    """
    获取回测每日记录
    
    Args:
        summary_id: 汇总ID
    
    Returns:
        每日记录列表
    """
    sql = """
        SELECT 
            trade_date, nav, cash_pool, wait_pool,
            holdings_value, total_value, drawdown, fee_cum
        FROM backtest_daily
        WHERE summary_id = %s
        ORDER BY trade_date ASC
    """
    return execute_query(sql, (summary_id,))


def get_backtest_trades(summary_id: int) -> List[Dict]:
    """
    获取回测成交记录
    
    Args:
        summary_id: 汇总ID
    
    Returns:
        成交记录列表
    """
    sql = """
        SELECT 
            trade_date, side, amount, price, shares, fee, reasons
        FROM backtest_trades
        WHERE summary_id = %s
        ORDER BY trade_date ASC, id ASC
    """
    return execute_query(sql, (summary_id,))


def run_backtest(
    product_id: int,
    strategy_key: str,
    strategy_version: Optional[str] = None,
    param_set_id: str = "default",
    params: Dict[str, Any] = None,
    initial_cash: float = 10000.0,
    monthly_deposit: Optional[float] = None,
    deposit_day: int = 10,
    min_trade_amount: float = 1000.0,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None
) -> Dict[str, Any]:
    """
    运行回测
    
    Args:
        product_id: 产品ID
        strategy_key: 策略标识
        strategy_version: 策略版本
        param_set_id: 参数组合ID
        params: 策略参数字典
        initial_cash: 初始现金
        monthly_deposit: 每月入金
        deposit_day: 入金日期
        min_trade_amount: 最小成交金额
        start_date: 开始日期
        end_date: 结束日期
    
    Returns:
        回测结果字典
    """
    try:
        # 获取产品信息
        product = get_product_by_id(product_id)
        if not product:
            raise ValueError(f"产品不存在: product_id={product_id}")
        
        is_exchange = product.get('channel') == 'EXCHANGE'
        
        # 创建数据提供者
        data_provider = DataProvider(auto_fetch=True)
        
        # 创建资金模型
        cash_model = CashModel(
            initial_cash=initial_cash,
            min_trade_amount=min_trade_amount,
            monthly_deposit=monthly_deposit,
            deposit_day=deposit_day
        )
        
        # 处理参数：将字符串类型的列表参数转换为实际列表
        processed_params = {}
        if params:
            for param_name, param_value in params.items():
                # 如果参数值已经是列表，直接使用
                if isinstance(param_value, list):
                    processed_params[param_name] = param_value
                # 如果是字符串且看起来像JSON列表，尝试解析
                elif isinstance(param_value, str) and param_value.strip().startswith('['):
                    try:
                        parsed = json.loads(param_value)
                        if isinstance(parsed, list):
                            processed_params[param_name] = parsed
                        else:
                            processed_params[param_name] = param_value
                    except (json.JSONDecodeError, TypeError):
                        processed_params[param_name] = param_value
                else:
                    processed_params[param_name] = param_value
        
        # 创建策略
        strategy_class = get_strategy(strategy_key, strategy_version)
        strategy = strategy_class(processed_params)
        
        # 创建回测引擎
        backtester = Backtester(
            data_provider=data_provider,
            cash_model=cash_model,
            strategy=strategy,
            product_id=product_id,
            is_exchange=is_exchange,
            start_date=start_date,
            end_date=end_date
        )
        
        # 执行回测
        result = backtester.run()
        
        return {
            'success': True,
            'summary_id': result.get('summary_id'),
            'metrics': result.get('metrics', {}),
            'message': '回测完成'
        }
    
    except Exception as e:
        logger.error(f"回测执行失败: {e}", exc_info=True)
        return {
            'success': False,
            'error': str(e),
            'message': f'回测失败: {e}'
        }


def delete_backtest_summary(summary_id: int) -> bool:
    """
    删除回测汇总及其关联数据
    
    Args:
        summary_id: 汇总ID
    
    Returns:
        是否删除成功
    """
    from data.db_connector import execute_one
    
    try:
        # 删除关联的每日记录
        execute_one("DELETE FROM backtest_daily WHERE summary_id = %s", (summary_id,))
        
        # 删除关联的成交记录
        execute_one("DELETE FROM backtest_trades WHERE summary_id = %s", (summary_id,))
        
        # 删除汇总记录
        execute_one("DELETE FROM backtest_summary WHERE id = %s", (summary_id,))
        
        return True
    except Exception as e:
        logger.error(f"删除回测汇总失败: {e}", exc_info=True)
        return False


def get_product_data_range(product_id: int) -> Optional[Dict]:
    """
    获取产品的行情数据范围
    
    Args:
        product_id: 产品ID
    
    Returns:
        数据范围信息，格式：{earliest_date, latest_date, record_count, channel}
    """
    from data.db_connector import execute_one
    
    # 获取产品信息
    product = get_product_by_id(product_id)
    if not product:
        return None
    
    channel = product.get('channel', 'OTC')
    product_code = product.get('code', '')
    
    if channel == 'EXCHANGE':
        # 场内：从 market_bar_d 表查询
        sql = """
            SELECT 
                MIN(trade_date) as earliest_date,
                MAX(trade_date) as latest_date,
                COUNT(*) as record_count
            FROM market_bar_d
            WHERE product_id = %s
        """
        row = execute_one(sql, (product_id,))
    else:
        # 场外：从 nav 表查询
        sql = """
            SELECT 
                MIN(nav_date) as earliest_date,
                MAX(nav_date) as latest_date,
                COUNT(*) as record_count
            FROM nav
            WHERE product_code = %s
        """
        row = execute_one(sql, (product_code,))
    
    if not row or not row.get('earliest_date'):
        return {
            'earliest_date': None,
            'latest_date': None,
            'record_count': 0,
            'channel': channel
        }
    
    earliest = row.get('earliest_date')
    latest = row.get('latest_date')
    
    return {
        'earliest_date': earliest.strftime('%Y-%m-%d') if earliest else None,
        'latest_date': latest.strftime('%Y-%m-%d') if latest else None,
        'record_count': int(row.get('record_count', 0)),
        'channel': channel
    }


def run_param_comparison(
    product_id: int,
    strategy_key: str,
    strategy_version: Optional[str] = None,
    param_sets: List[Dict[str, Any]] = None,
    initial_cash: float = 10000.0,
    monthly_deposit: Optional[float] = None,
    deposit_day: int = 10,
    min_trade_amount: float = 1000.0,
    start_date: Optional[date] = None,
    end_date: Optional[date] = None
) -> Dict[str, Any]:
    """
    运行参数组合对比
    
    Args:
        product_id: 产品ID
        strategy_key: 策略标识
        strategy_version: 策略版本
        param_sets: 参数组合列表
        initial_cash: 初始现金
        monthly_deposit: 每月入金
        deposit_day: 入金日期
        min_trade_amount: 最小成交金额
        start_date: 开始日期
        end_date: 结束日期
    
    Returns:
        对比结果字典
    """
    try:
        # 获取产品信息
        product = get_product_by_id(product_id)
        if not product:
            raise ValueError(f"产品不存在: product_id={product_id}")
        
        is_exchange = product.get('channel') == 'EXCHANGE'
        
        # 创建数据提供者
        data_provider = DataProvider(auto_fetch=True)
        
        # 创建参数运行器
        param_runner = ParamRunner(
            data_provider=data_provider,
            product_id=product_id,
            strategy_key=strategy_key,
            strategy_version=strategy_version,
            is_exchange=is_exchange
        )
        
        # 执行参数组合对比
        results = param_runner.run_param_sets(
            param_sets=param_sets or [],
            initial_cash=initial_cash,
            monthly_deposit=monthly_deposit,
            deposit_day=deposit_day,
            min_trade_amount=min_trade_amount,
            start_date=start_date,
            end_date=end_date
        )
        
        return {
            'success': True,
            'results': results,
            'message': f'参数对比完成，共 {len(results)} 组参数'
        }
    
    except Exception as e:
        logger.error(f"参数对比执行失败: {e}", exc_info=True)
        return {
            'success': False,
            'error': str(e),
            'message': f'参数对比失败: {e}'
        }

