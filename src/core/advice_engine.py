# -*- coding: utf-8 -*-
"""
AdviceEngine - 生产判断层

根据实时行情、资金池状态、产品参数，输出买入建议。
不自动下单，只提供建议。
"""

from dataclasses import dataclass
from datetime import datetime
from typing import List, Optional, Dict, Any
from decimal import Decimal
import logging

from .premium_brake import apply_premium_brake, get_recommended_limit_price
from .market_quote_service import get_latest_premium, get_daily_bars
from data.product_service import get_product_by_id
from data.db_connector import execute_query

logger = logging.getLogger(__name__)


@dataclass
class Advice:
    """
    买入建议
    
    Attributes:
        product_id: 产品ID
        status: 建议状态 (BUY / BUY_HALF / WAIT)
        suggested_amount: 建议买入金额
        suggested_limit_price: 建议限价
        reasons: 原因列表（必须可解释）
        timestamp: 建议时间
        suggested_time_window: 建议下单时间窗口（如 "10:30-11:15 / 13:30-14:30"）
    """
    product_id: int
    status: str  # BUY / BUY_HALF / WAIT
    suggested_amount: float
    suggested_limit_price: float
    reasons: List[str]
    timestamp: datetime
    suggested_time_window: Optional[str] = "10:30-11:15 / 13:30-14:30"


class AdviceEngine:
    """
    建议引擎
    
    判断逻辑：
    1. 溢价刹车（QDII）：复用 premium_brake.apply_premium_brake()
    2. 分位判断：计算滚动N日分位，低于p20建议买
    3. 回撤判断：计算相对高点回撤，触发加仓建议
    4. 资金检查：检查 cash_pool 是否足够，是否满足最小成交额
    """
    
    # 默认参数
    DEFAULT_MIN_TRADE_AMOUNT = 1000.0
    DEFAULT_PERCENTILE_WINDOW = 250
    DEFAULT_BUY_PERCENTILE = 20
    DEFAULT_DRAWDOWN_THRESHOLDS = [0.02, 0.04, 0.08]  # 2%, 4%, 8%
    
    def __init__(self):
        """初始化建议引擎"""
        pass
    
    def generate_advice(
        self,
        product_id: int,
        planned_amount: float,
        cash_pool: float = 0.0,
        wait_pool: float = 0.0,
        current_price: Optional[float] = None,
        min_trade_amount: Optional[float] = None
    ) -> Advice:
        """
        生成买入建议
        
        Args:
            product_id: 产品ID
            planned_amount: 计划买入金额
            cash_pool: 可用现金池
            wait_pool: 等待池
            current_price: 当前价格（如果提供，否则从数据库获取）
            min_trade_amount: 最小成交金额（如果提供，否则使用默认值）
        
        Returns:
            Advice: 买入建议
        """
        # 获取产品信息
        product = get_product_by_id(product_id)
        if not product:
            return Advice(
                product_id=product_id,
                status="WAIT",
                suggested_amount=0.0,
                suggested_limit_price=0.0,
                reasons=["产品不存在"],
                timestamp=datetime.now()
            )
        
        is_qdii = product.get('is_qdii', False)
        product_code = product.get('code', '')
        channel = product.get('channel', 'OTC')
        
        # 最小成交金额
        if min_trade_amount is None:
            min_trade_amount = self.DEFAULT_MIN_TRADE_AMOUNT
        
        reasons = []
        status = "WAIT"
        suggested_amount = 0.0
        suggested_limit_price = 0.0
        
        # 1. 资金检查
        available_cash = cash_pool + wait_pool
        if available_cash < min_trade_amount:
            return Advice(
                product_id=product_id,
                status="WAIT",
                suggested_amount=0.0,
                suggested_limit_price=0.0,
                reasons=[f"资金不足（可用{available_cash:.2f}，需要{min_trade_amount:.2f}）"],
                timestamp=datetime.now()
            )
        
        # 限制计划金额不超过可用资金
        planned_amount = min(planned_amount, available_cash)
        
        # 2. 溢价刹车（QDII）
        premium_rate = None
        if is_qdii and channel == 'EXCHANGE':
            premium_data = get_latest_premium(product_id)
            if premium_data:
                premium_rate = float(premium_data.get('premium_rate', 0))
            
            if premium_rate is not None:
                # 应用溢价刹车
                brake_result = apply_premium_brake(
                    Decimal(str(planned_amount)),
                    Decimal(str(premium_rate))
                )
                
                executed_amount = float(brake_result['executed_amount'])
                pending_amount = float(brake_result['pending_amount'])
                brake_reason = brake_result['reason']
                
                reasons.append(f"溢价刹车: {brake_reason}")
                
                if executed_amount <= 0:
                    return Advice(
                        product_id=product_id,
                        status="WAIT",
                        suggested_amount=0.0,
                        suggested_limit_price=0.0,
                        reasons=reasons,
                        timestamp=datetime.now()
                    )
                elif pending_amount > 0:
                    status = "BUY_HALF"
                    suggested_amount = executed_amount
                    reasons.append(f"部分买入（{executed_amount:.2f}），剩余进入等待池（{pending_amount:.2f}）")
                else:
                    status = "BUY"
                    suggested_amount = executed_amount
            else:
                reasons.append("QDII产品但无溢价数据，按正常买入处理")
                status = "BUY"
                suggested_amount = planned_amount
        else:
            # 非QDII，正常买入
            status = "BUY"
            suggested_amount = planned_amount
        
        # 3. 分位判断（如果有历史数据）
        if current_price is None:
            # 从数据库获取最新价格
            if channel == 'EXCHANGE':
                bars = get_daily_bars(product_id, None, None)
                if bars:
                    current_price = float(bars[-1].get('close', 0))
            else:
                # 场外：从 nav 表获取
                from data.nav_reader import get_latest_nav
                nav_data = get_latest_nav(product_code)
                if nav_data:
                    current_price = float(nav_data[1])
        
        if current_price and current_price > 0:
            # 计算分位
            percentile = self._calculate_percentile(product_id, current_price, channel)
            if percentile is not None:
                if percentile <= self.DEFAULT_BUY_PERCENTILE:
                    reasons.append(f"价格分位 {percentile:.1f}% <= {self.DEFAULT_BUY_PERCENTILE}%（低位，适合买入）")
                elif percentile >= 80:
                    reasons.append(f"价格分位 {percentile:.1f}% >= 80%（高位，谨慎买入）")
                    if status == "BUY":
                        status = "WAIT"
                        suggested_amount = 0.0
        
        # 4. 回撤判断（如果有历史数据）
        if current_price and current_price > 0:
            drawdown = self._calculate_drawdown(product_id, current_price, channel)
            if drawdown is not None:
                abs_drawdown = abs(drawdown)
                if abs_drawdown >= 0.08:  # 8%回撤
                    reasons.append(f"回撤 {abs_drawdown*100:.1f}%（深度回撤，适合加仓）")
                    if status == "WAIT":
                        status = "BUY"
                        suggested_amount = planned_amount
                elif abs_drawdown >= 0.04:  # 4%回撤
                    reasons.append(f"回撤 {abs_drawdown*100:.1f}%（中等回撤）")
        
        # 5. 计算建议限价
        if current_price and current_price > 0:
            suggested_limit_price = float(get_recommended_limit_price(Decimal(str(current_price))))
        else:
            suggested_limit_price = 0.0
        
        # 6. 最终检查：确保建议金额满足最小成交额
        if suggested_amount > 0 and suggested_amount < min_trade_amount:
            reasons.append(f"建议金额 {suggested_amount:.2f} < 最小成交额 {min_trade_amount:.2f}，调整为等待")
            status = "WAIT"
            suggested_amount = 0.0
        
        return Advice(
            product_id=product_id,
            status=status,
            suggested_amount=suggested_amount,
            suggested_limit_price=suggested_limit_price,
            reasons=reasons,
            timestamp=datetime.now()
        )
    
    def _calculate_percentile(
        self,
        product_id: int,
        current_price: float,
        channel: str
    ) -> Optional[float]:
        """
        计算价格分位
        
        Args:
            product_id: 产品ID
            current_price: 当前价格
            channel: 渠道（EXCHANGE/OTC）
        
        Returns:
            分位（0-100），None 如果数据不足
        """
        try:
            if channel == 'EXCHANGE':
                # 从 market_bar_d 表获取历史价格
                sql = """
                    SELECT close_price
                    FROM market_bar_d
                    WHERE product_id = %s
                    ORDER BY trade_date DESC
                    LIMIT %s
                """
                rows = execute_query(sql, (product_id, self.DEFAULT_PERCENTILE_WINDOW))
                if len(rows) < self.DEFAULT_PERCENTILE_WINDOW:
                    return None
                
                prices = [float(row['close_price']) for row in rows[:-1]]  # 排除当前价格
            else:
                # 场外：从 nav 表获取
                product = get_product_by_id(product_id)
                if not product:
                    return None
                
                product_code = product.get('code', '')
                sql = """
                    SELECT nav
                    FROM nav
                    WHERE product_code = %s
                    ORDER BY nav_date DESC
                    LIMIT %s
                """
                rows = execute_query(sql, (product_code, self.DEFAULT_PERCENTILE_WINDOW))
                if len(rows) < self.DEFAULT_PERCENTILE_WINDOW:
                    return None
                
                prices = [float(row['nav']) for row in rows[:-1]]  # 排除当前价格
            
            if not prices:
                return None
            
            # 计算分位
            below_count = sum(1 for p in prices if p < current_price)
            percentile = (below_count / len(prices)) * 100
            
            return percentile
        
        except Exception as e:
            logger.warning(f"计算分位失败: {e}")
            return None
    
    def _calculate_drawdown(
        self,
        product_id: int,
        current_price: float,
        channel: str
    ) -> Optional[float]:
        """
        计算回撤
        
        Args:
            product_id: 产品ID
            current_price: 当前价格
            channel: 渠道（EXCHANGE/OTC）
        
        Returns:
            回撤（负数，如 -0.08 表示 8%），None 如果数据不足
        """
        try:
            if channel == 'EXCHANGE':
                # 从 market_bar_d 表获取历史价格
                sql = """
                    SELECT close_price
                    FROM market_bar_d
                    WHERE product_id = %s
                    ORDER BY trade_date DESC
                    LIMIT 250
                """
                rows = execute_query(sql, (product_id,))
            else:
                # 场外：从 nav 表获取
                product = get_product_by_id(product_id)
                if not product:
                    return None
                
                product_code = product.get('code', '')
                sql = """
                    SELECT nav
                    FROM nav
                    WHERE product_code = %s
                    ORDER BY nav_date DESC
                    LIMIT 250
                """
                rows = execute_query(sql, (product_code,))
            
            if not rows:
                return None
            
            # 找到峰值
            prices = [float(row.get('close_price' if channel == 'EXCHANGE' else 'nav', 0)) for row in rows]
            peak_price = max(prices)
            
            # 计算回撤
            drawdown = (current_price - peak_price) / peak_price
            
            return drawdown
        
        except Exception as e:
            logger.warning(f"计算回撤失败: {e}")
            return None

