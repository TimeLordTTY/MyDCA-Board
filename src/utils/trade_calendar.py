"""交易日历模块 - 统一交易日入口

所有交易日相关逻辑必须通过此模块，其他模块不得直接 import 第三方节假日库。

规则：
- 交易日 = 周一~周五，排除法定节假日
- 使用 chinese_calendar 库判断，ImportError 时降级为仅判断周一~周五
"""
from datetime import date, datetime, timedelta
from typing import Union

# 尝试导入 chinese_calendar，失败则降级
try:
    import chinese_calendar
    HAS_CHINESE_CALENDAR = True
except ImportError:
    HAS_CHINESE_CALENDAR = False


def _to_date(d: Union[str, date, datetime]) -> date:
    """将输入转换为 date 对象"""
    if isinstance(d, datetime):
        return d.date()
    if isinstance(d, str):
        return datetime.strptime(d, '%Y-%m-%d').date()
    return d


def is_trade_day(d: Union[str, date, datetime]) -> bool:
    """
    判断是否为交易日
    
    Args:
        d: 日期（支持 str 'YYYY-MM-DD'、date、datetime）
    
    Returns:
        bool: 是否为交易日
    """
    d = _to_date(d)
    if HAS_CHINESE_CALENDAR:
        return chinese_calendar.is_workday(d)
    else:
        # 降级：仅判断周一~周五
        return d.weekday() < 5


def next_trade_day(d: Union[str, date, datetime]) -> date:
    """
    获取下一个交易日（不包含当天）
    
    Args:
        d: 起始日期
    
    Returns:
        date: 下一个交易日
    """
    d = _to_date(d)
    next_d = d + timedelta(days=1)
    while not is_trade_day(next_d):
        next_d += timedelta(days=1)
    return next_d


def prev_trade_day(d: Union[str, date, datetime]) -> date:
    """
    获取上一个交易日（不包含当天）
    
    Args:
        d: 起始日期
    
    Returns:
        date: 上一个交易日
    """
    d = _to_date(d)
    prev_d = d - timedelta(days=1)
    while not is_trade_day(prev_d):
        prev_d -= timedelta(days=1)
    return prev_d


def add_trade_days(d: Union[str, date, datetime], n: int) -> date:
    """
    增加 n 个交易日
    
    Args:
        d: 起始日期
        n: 增加的交易日数（必须 >= 0）
    
    Returns:
        date: 目标日期
    """
    d = _to_date(d)
    if n < 0:
        raise ValueError("n must be >= 0, use subtract_trade_days for negative")
    
    result = d
    for _ in range(n):
        result = next_trade_day(result)
    return result


def subtract_trade_days(d: Union[str, date, datetime], n: int) -> date:
    """
    减少 n 个交易日
    
    Args:
        d: 起始日期
        n: 减少的交易日数（必须 >= 0）
    
    Returns:
        date: 目标日期
    """
    d = _to_date(d)
    if n < 0:
        raise ValueError("n must be >= 0, use add_trade_days for negative")
    
    result = d
    for _ in range(n):
        result = prev_trade_day(result)
    return result


def get_trade_day_or_next(d: Union[str, date, datetime]) -> date:
    """
    如果当天是交易日则返回当天，否则返回下一个交易日
    
    Args:
        d: 日期
    
    Returns:
        date: 当天（如果是交易日）或下一个交易日
    """
    d = _to_date(d)
    if is_trade_day(d):
        return d
    return next_trade_day(d)


