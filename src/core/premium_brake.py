"""溢价刹车逻辑"""
import logging
from decimal import Decimal, ROUND_HALF_UP
from typing import Tuple, Optional, Dict

logger = logging.getLogger(__name__)


def apply_premium_brake(planned_amount: Decimal, premium_rate: Optional[Decimal] = None) -> Dict:
    """
    应用溢价刹车规则（新接口，兼容UI调用）
    
    规则：
    - premium <= 0.01 (1%): 正常买（100%）
    - 0.01 < premium <= 0.02 (1%-2%): 买一半（50%），剩余进入待买入池
    - premium > 0.02 (2%): 不买（0%），全部进入待买入池
    
    Args:
        planned_amount: 计划买入金额
        premium_rate: 溢价率（如 0.0123 表示 1.23%），None 表示非QDII或无数据
    
    Returns:
        {'executed_amount': Decimal, 'pending_amount': Decimal, 'reason': str} 字典
    """
    if premium_rate is None:
        return {
            'executed_amount': planned_amount,
            'pending_amount': Decimal('0'),
            'reason': '非QDII或无溢价数据，全额买入'
        }
    
    premium = float(premium_rate)
    
    if premium <= 0.01:
        # 正常买（100%）
        executed = planned_amount
        pending = Decimal('0')
        reason = f"溢价率 {premium:.2%}，正常买入"
    elif premium <= 0.02:
        # 买一半（50%）
        executed = planned_amount * Decimal('0.5')
        pending = planned_amount - executed
        reason = f"溢价率 {premium:.2%}，买入一半"
    else:
        # 不买（0%）
        executed = Decimal('0')
        pending = planned_amount
        reason = f"溢价率 {premium:.2%} 过高，暂停买入"
    
    logger.info(f"溢价刹车: premium={premium:.4%}, planned={planned_amount}, "
                f"executed={executed}, pending={pending}")
    
    return {
        'executed_amount': executed,
        'pending_amount': pending,
        'reason': reason
    }


def apply_premium_brake_old(premium_rate: Decimal, planned_amount: Decimal) -> Tuple[Decimal, Decimal]:
    """
    应用溢价刹车规则（旧接口，保留以兼容历史代码）
    
    Args:
        premium_rate: 溢价率（如 0.0123 表示 1.23%）
        planned_amount: 计划买入金额
    
    Returns:
        (executed_amount, pending_amount) 元组
    """
    result = apply_premium_brake(planned_amount, premium_rate)
    return result['executed_amount'], result['pending_amount']


def get_recommended_limit_price(last_price: Decimal) -> Decimal:
    """
    计算推荐的限价（保守限价）
    
    Args:
        last_price: 最新价格
    
    Returns:
        推荐限价（在最新价基础上打99.9%折扣）
    """
    if last_price is None or last_price <= 0:
        return Decimal('0')
    # 在最新价基础上打99.9%折扣，并保留3位小数
    limit_price = (last_price * Decimal('0.999')).quantize(Decimal('0.001'), rounding=ROUND_HALF_UP)
    return limit_price

