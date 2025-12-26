# -*- coding: utf-8 -*-
"""
ParamRunner - 参数组合对比运行器

同一个策略，跑多组参数，汇总到 backtest_summary 表。
"""

from datetime import date
from typing import List, Dict, Any, Optional
import logging

from ..data.provider import DataProvider
from ..account.cash_model import CashModel
from ..framework.base import Strategy
from ..framework.registry import get_strategy
from .backtester import Backtester

logger = logging.getLogger(__name__)


class ParamRunner:
    """
    参数组合对比运行器
    
    功能：
    - 同一个策略，跑多组参数
    - 汇总到 backtest_summary 表（多行）
    - 每行包含：strategy_key, param_set_id, metrics...
    """
    
    def __init__(
        self,
        data_provider: DataProvider,
        product_id: int,
        strategy_key: str,
        strategy_version: Optional[str] = None,
        is_exchange: bool = True
    ):
        """
        初始化参数运行器
        
        Args:
            data_provider: 数据提供者
            product_id: 产品ID
            strategy_key: 策略标识
            strategy_version: 策略版本（None 使用默认）
            is_exchange: 是否为场内交易
        """
        self.data_provider = data_provider
        self.product_id = product_id
        self.strategy_key = strategy_key
        self.strategy_version = strategy_version
        self.is_exchange = is_exchange
    
    def run_param_sets(
        self,
        param_sets: List[Dict[str, Any]],
        initial_cash: float,
        monthly_deposit: Optional[float] = None,
        deposit_day: int = 10,
        min_trade_amount: float = 1000.0,
        start_date: Optional[date] = None,
        end_date: Optional[date] = None
    ) -> List[Dict[str, Any]]:
        """
        运行多组参数
        
        Args:
            param_sets: 参数组合列表，每项为参数字典
            initial_cash: 初始现金
            monthly_deposit: 每月入金金额
            deposit_day: 每月入金日期
            min_trade_amount: 最小成交金额
            start_date: 开始日期
            end_date: 结束日期
        
        Returns:
            结果列表，每项包含 summary_id 和 metrics
        """
        results = []
        
        # 获取策略类
        try:
            strategy_class = get_strategy(self.strategy_key, self.strategy_version)
        except ValueError as e:
            logger.error(f"获取策略失败: {e}")
            return results
        
        logger.info(f"开始参数组合对比: strategy={self.strategy_key}, param_sets={len(param_sets)}")
        
        for i, param_set in enumerate(param_sets):
            param_set_id = param_set.get('param_set_id', f"param_{i+1}")
            params = param_set.get('params', param_set)  # 如果直接是参数字典，则使用
            
            logger.info(f"运行参数组合 {i+1}/{len(param_sets)}: param_set_id={param_set_id}")
            
            # 创建资金模型
            cash_model = CashModel(
                initial_cash=initial_cash,
                min_trade_amount=min_trade_amount,
                monthly_deposit=monthly_deposit,
                deposit_day=deposit_day
            )
            
            # 创建策略实例
            strategy = strategy_class(params)
            
            # 创建回测引擎
            backtester = Backtester(
                data_provider=self.data_provider,
                cash_model=cash_model,
                strategy=strategy,
                product_id=self.product_id,
                is_exchange=self.is_exchange,
                start_date=start_date,
                end_date=end_date
            )
            
            # 执行回测
            try:
                result = backtester.run()
                
                if result.get('summary_id'):
                    results.append({
                        'param_set_id': param_set_id,
                        'summary_id': result['summary_id'],
                        'metrics': result.get('metrics', {})
                    })
                    logger.info(f"参数组合 {param_set_id} 完成: summary_id={result['summary_id']}")
                else:
                    logger.warning(f"参数组合 {param_set_id} 未生成汇总记录")
            
            except Exception as e:
                logger.error(f"参数组合 {param_set_id} 执行失败: {e}", exc_info=True)
                continue
        
        logger.info(f"参数组合对比完成: 成功 {len(results)}/{len(param_sets)}")
        return results

