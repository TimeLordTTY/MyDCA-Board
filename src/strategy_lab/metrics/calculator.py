# -*- coding: utf-8 -*-
"""
MetricsCalculator - 指标计算器

计算回测指标：年化收益、最大回撤、成交次数等。
"""

from typing import List, Dict, Any
import math


class MetricsCalculator:
    """
    指标计算器
    
    计算指标：
    - equity_curve: 每日净资产曲线
    - annual_return: 年化收益（基于净资产曲线）
    - max_drawdown: 最大回撤（基于净资产曲线）
    - trade_count: 成交次数
    - avg_monthly_trades: 平均月成交次数
    - total_fees: 手续费总额
    - fee_ratio: 手续费占收益比例
    - wait_pool_ratio: 资金长期滞留在 wait_pool 的比例
    """
    
    @staticmethod
    def calculate(
        daily_records: List[Dict[str, Any]],
        trades: List[Dict[str, Any]],
        initial_cash: float,
        start_date: Any,
        end_date: Any
    ) -> Dict[str, Any]:
        """
        计算所有指标
        
        Args:
            daily_records: 每日记录列表，每项包含：
                - trade_date: 日期
                - total_value: 总资产
                - cash_pool: 现金池
                - wait_pool: 等待池
                - fee_cum: 累计手续费
            trades: 成交记录列表
            initial_cash: 初始现金
            start_date: 开始日期
            end_date: 结束日期
        
        Returns:
            指标字典
        """
        if not daily_records:
            return MetricsCalculator._empty_metrics()
        
        # 计算总收益率
        final_value = daily_records[-1].get('total_value', initial_cash)
        
        # initial_cash 现在已经是累计投入（从 backtester 传递过来）
        # 如果 initial_cash 为 0 或很小，尝试从第一条记录反推
        total_invested = initial_cash
        if total_invested <= 0.01:
            # 从第一条记录的总资产减去已买入金额来估算初始现金
            first_record = daily_records[0] if daily_records else {}
            first_value = first_record.get('total_value', 0.0)
            # 估算：假设第一条记录时还没有买入，总资产就是初始现金
            total_invested = first_value
        
        # 如果 total_invested 仍然为 0，使用 final_value 作为基准（避免除零）
        if total_invested <= 0.01:
            total_invested = max(final_value, 1.0)  # 至少为1，避免除零
        
        total_return = (final_value - total_invested) / total_invested if total_invested > 0 else 0.0
        
        # 计算年化收益率
        days = (end_date - start_date).days if hasattr(end_date, '__sub__') else len(daily_records)
        years = max(days / 365.0, 1.0 / 365.0)  # 至少1天
        # 使用累计投入作为基准计算年化收益
        if total_invested > 0.01:
            annual_return = ((final_value / total_invested) ** (1.0 / years) - 1.0)
        else:
            annual_return = 0.0
        
        # 计算最大回撤
        max_drawdown = MetricsCalculator._calculate_max_drawdown(daily_records)
        
        # 成交次数
        trade_count = len(trades)
        
        # 平均月成交次数
        months = max(years * 12, 1.0 / 12.0)
        avg_monthly_trades = trade_count / months if months > 0 else 0.0
        
        # 手续费总额
        total_fees = daily_records[-1].get('fee_cum', 0.0) if daily_records else 0.0
        
        # 手续费占收益比例
        total_profit = final_value - initial_cash
        fee_ratio = total_fees / total_profit if total_profit > 0 else 0.0
        
        # wait_pool 滞留比例（平均 wait_pool / 平均 total_value）
        wait_pool_sum = sum(r.get('wait_pool', 0.0) for r in daily_records)
        total_value_sum = sum(r.get('total_value', 0.0) for r in daily_records)
        wait_pool_ratio = wait_pool_sum / total_value_sum if total_value_sum > 0 else 0.0
        
        return {
            'total_return': total_return,
            'annual_return': annual_return,
            'max_drawdown': max_drawdown,
            'trade_count': trade_count,
            'avg_monthly_trades': avg_monthly_trades,
            'total_fees': total_fees,
            'fee_ratio': fee_ratio,
            'wait_pool_ratio': wait_pool_ratio,
            'final_value': final_value,
            'initial_cash': initial_cash,
            'total_invested': total_invested
        }
    
    @staticmethod
    def _calculate_max_drawdown(daily_records: List[Dict[str, Any]]) -> float:
        """
        计算最大回撤
        
        回撤 = (峰值 - 当前值) / 峰值
        最大回撤 = max(所有回撤)
        
        Args:
            daily_records: 每日记录列表
        
        Returns:
            最大回撤（正数，如 0.15 表示 15%）
        """
        if not daily_records:
            return 0.0
        
        max_drawdown = 0.0
        peak_value = daily_records[0].get('total_value', 0.0)
        
        for record in daily_records:
            current_value = record.get('total_value', 0.0)
            
            # 更新峰值
            if current_value > peak_value:
                peak_value = current_value
            
            # 计算回撤
            if peak_value > 0:
                drawdown = (peak_value - current_value) / peak_value
                if drawdown > max_drawdown:
                    max_drawdown = drawdown
        
        return max_drawdown
    
    @staticmethod
    def _empty_metrics() -> Dict[str, Any]:
        """返回空指标"""
        return {
            'total_return': 0.0,
            'annual_return': 0.0,
            'max_drawdown': 0.0,
            'trade_count': 0,
            'avg_monthly_trades': 0.0,
            'total_fees': 0.0,
            'fee_ratio': 0.0,
            'wait_pool_ratio': 0.0,
            'final_value': 0.0,
            'initial_cash': 0.0
        }

