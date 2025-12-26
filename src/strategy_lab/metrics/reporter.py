# -*- coding: utf-8 -*-
"""
Reporter - 报告生成器

将回测结果输出到数据库表（不输出CSV）。
"""

from datetime import date
from typing import List, Dict, Any, Optional
import json
import logging

import sys
from pathlib import Path
project_root = Path(__file__).parent.parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))
from data.db_connector import execute_insert, execute_many

logger = logging.getLogger(__name__)


class Reporter:
    """
    报告生成器
    
    输出到数据库：
    - backtest_summary: 汇总结果
    - backtest_daily: 每日数据
    - backtest_trades: 逐笔成交
    """
    
    @staticmethod
    def save_summary(
        product_id: int,
        strategy_key: str,
        strategy_version: str,
        param_set_id: str,
        start_date: date,
        end_date: date,
        metrics: Dict[str, Any],
        strategy_config: Optional[Dict[str, Any]] = None
    ) -> Optional[int]:
        """
        保存汇总结果
        
        Args:
            product_id: 产品ID
            strategy_key: 策略标识
            strategy_version: 策略版本
            param_set_id: 参数组合ID
            start_date: 开始日期
            end_date: 结束日期
            metrics: 指标字典
        
        Returns:
            插入的 summary_id
        """
        sql = """
            INSERT INTO backtest_summary (
                product_id, strategy_key, strategy_version, param_set_id,
                start_date, end_date, initial_cash, final_value,
                total_return, annual_return, max_drawdown, trade_count,
                total_fees, fee_ratio, wait_pool_ratio
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
            )
        """
        
        # 使用 total_invested 作为累计投入，如果没有则使用 initial_cash
        total_invested = metrics.get('total_invested', 0.0) or metrics.get('initial_cash', 0.0)
        
        params = (
            product_id,
            strategy_key,
            strategy_version,
            param_set_id,
            start_date,
            end_date,
            total_invested,  # 保存累计投入
            metrics.get('final_value', 0.0),
            metrics.get('total_return', 0.0),
            metrics.get('annual_return', 0.0),
            metrics.get('max_drawdown', 0.0),
            metrics.get('trade_count', 0),
            metrics.get('total_fees', 0.0),
            metrics.get('fee_ratio', 0.0),
            metrics.get('wait_pool_ratio', 0.0)
        )
        
        try:
            summary_id = execute_insert(sql, params)
            logger.info(f"保存回测汇总成功: summary_id={summary_id}")
            
            # 同时保存参数到 strategy_config 表（如果提供了参数且表中不存在）
            if strategy_config is not None:
                Reporter._save_strategy_config(
                    strategy_key, strategy_version, param_set_id, strategy_config
                )
            
            return summary_id
        except Exception as e:
            logger.error(f"保存回测汇总失败: {e}", exc_info=True)
            return None
    
    @staticmethod
    def _save_strategy_config(
        strategy_key: str,
        strategy_version: str,
        param_set_id: str,
        params: Dict[str, Any]
    ) -> None:
        """
        保存策略参数配置到 strategy_config 表
        
        Args:
            strategy_key: 策略标识
            strategy_version: 策略版本
            param_set_id: 参数组合ID
            params: 参数字典
        """
        try:
            param_json = json.dumps(params, ensure_ascii=False)
            
            sql = """
                INSERT INTO strategy_config (
                    strategy_key, strategy_version, param_set_id, param_json, is_active
                ) VALUES (%s, %s, %s, %s, 1)
                ON DUPLICATE KEY UPDATE
                    param_json = VALUES(param_json),
                    updated_at = CURRENT_TIMESTAMP
            """
            
            from data.db_connector import execute_one
            execute_one(sql, (strategy_key, strategy_version, param_set_id, param_json))
            logger.debug(f"保存策略配置成功: {strategy_key}@{strategy_version}#{param_set_id}")
        except Exception as e:
            logger.warning(f"保存策略配置失败: {e}", exc_info=True)
    
    @staticmethod
    def save_daily_records(
        summary_id: int,
        daily_records: List[Dict[str, Any]]
    ) -> int:
        """
        保存每日记录
        
        Args:
            summary_id: 汇总ID
            daily_records: 每日记录列表，每项包含：
                - trade_date: 日期
                - nav: 净值/收盘价
                - cash_pool: 现金池
                - wait_pool: 等待池
                - holdings_value: 持仓市值
                - total_value: 总资产
                - drawdown: 当前回撤
                - fee_cum: 累计手续费
        
        Returns:
            保存的记录数
        """
        if not daily_records:
            return 0
        
        sql = """
            INSERT INTO backtest_daily (
                summary_id, trade_date, nav, cash_pool, wait_pool,
                holdings_value, total_value, drawdown, fee_cum
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s, %s
            )
            ON DUPLICATE KEY UPDATE
                nav = VALUES(nav),
                cash_pool = VALUES(cash_pool),
                wait_pool = VALUES(wait_pool),
                holdings_value = VALUES(holdings_value),
                total_value = VALUES(total_value),
                drawdown = VALUES(drawdown),
                fee_cum = VALUES(fee_cum)
        """
        
        params_list = []
        for record in daily_records:
            params_list.append((
                summary_id,
                record.get('trade_date'),
                record.get('nav', 0.0),
                record.get('cash_pool', 0.0),
                record.get('wait_pool', 0.0),
                record.get('holdings_value', 0.0),
                record.get('total_value', 0.0),
                record.get('drawdown', 0.0),
                record.get('fee_cum', 0.0)
            ))
        
        try:
            count = execute_many(sql, params_list)
            logger.info(f"保存每日记录成功: summary_id={summary_id}, count={count}")
            return count
        except Exception as e:
            logger.error(f"保存每日记录失败: {e}", exc_info=True)
            return 0
    
    @staticmethod
    def save_trades(
        summary_id: int,
        trades: List[Dict[str, Any]]
    ) -> int:
        """
        保存成交记录
        
        Args:
            summary_id: 汇总ID
            trades: 成交记录列表，每项包含：
                - trade_date: 日期
                - side: 买卖方向 (BUY/SELL)
                - amount: 成交金额
                - price: 成交价格
                - shares: 成交份额
                - fee: 手续费
                - reasons: 成交原因列表
        
        Returns:
            保存的记录数
        """
        if not trades:
            return 0
        
        sql = """
            INSERT INTO backtest_trades (
                summary_id, trade_date, side, amount, price, shares, fee, reasons
            ) VALUES (
                %s, %s, %s, %s, %s, %s, %s, %s
            )
        """
        
        params_list = []
        for trade in trades:
            # 将 reasons 列表转换为 JSON 字符串
            reasons_json = json.dumps(trade.get('reasons', []), ensure_ascii=False)
            
            params_list.append((
                summary_id,
                trade.get('trade_date'),
                trade.get('side'),
                trade.get('amount', 0.0),
                trade.get('price', 0.0),
                trade.get('shares', 0.0),
                trade.get('fee', 0.0),
                reasons_json
            ))
        
        try:
            count = execute_many(sql, params_list)
            logger.info(f"保存成交记录成功: summary_id={summary_id}, count={count}")
            return count
        except Exception as e:
            logger.error(f"保存成交记录失败: {e}", exc_info=True)
            return 0

