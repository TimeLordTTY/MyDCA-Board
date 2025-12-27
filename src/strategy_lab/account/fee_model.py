# -*- coding: utf-8 -*-
"""
FeeModel - 场内手续费模型

场内交易手续费：万0.845，0.2起收
"""

from typing import Optional


class FeeModel:
    """
    手续费计算模型
    
    场内手续费规则：
    - 费率：0.0000845（万0.845）
    - 最低收费：0.2 元
    - 公式：fee = max(amount * 0.0000845, 0.2)
    """
    
    # 场内手续费费率（万0.845）
    EXCHANGE_FEE_RATE = 0.0000845
    
    # 最低手续费（0.2元）
    MIN_FEE = 0.2
    
    # 印花税（默认0，留扩展口）
    STAMP_TAX_RATE = 0.0
    
    @classmethod
    def calculate(cls, amount: float, is_exchange: bool = True) -> float:
        """
        计算手续费
        
        Args:
            amount: 交易金额
            is_exchange: 是否为场内交易（默认True）
        
        Returns:
            手续费金额
        """
        if amount <= 0:
            return 0.0
        
        if is_exchange:
            # 场内：万0.845，0.2起收
            fee = max(amount * cls.EXCHANGE_FEE_RATE, cls.MIN_FEE)
        else:
            # 场外：使用费率（从产品配置读取，这里默认0）
            fee = 0.0
        
        # 印花税（目前默认0，留扩展口）
        stamp_tax = amount * cls.STAMP_TAX_RATE
        
        return fee + stamp_tax
    
    @classmethod
    def calculate_buy_fee(cls, amount: float, is_exchange: bool = True) -> float:
        """计算买入手续费（与 calculate 相同）"""
        return cls.calculate(amount, is_exchange)
    
    @classmethod
    def calculate_sell_fee(cls, amount: float, is_exchange: bool = True) -> float:
        """计算卖出手续费（与 calculate 相同）"""
        return cls.calculate(amount, is_exchange)



