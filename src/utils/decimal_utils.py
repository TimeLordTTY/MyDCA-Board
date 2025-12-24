#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Decimal 工具模块 - 统一精度与舍入规范

设计原则：
- 所有金额/份额/净值计算必须使用 Decimal，严禁 float
- 舍入只发生在输出层，内部计算保留高精度
- 与数据库 DECIMAL 精度保持一致

精度规范：
- shares: DECIMAL(20,6) - 保留6位小数
- money (amount/cost/value/pnl/fee/tax): DECIMAL(20,2) - 保留2位小数
- nav/price: DECIMAL(20,4) 或 DECIMAL(20,6) - 至少4位，可配置
"""
from decimal import Decimal, ROUND_HALF_UP, getcontext
from typing import Union

# 设置全局 Decimal 上下文（高精度）
getcontext().prec = 28  # 足够高的精度用于中间计算


def to_dec(x: Union[str, int, float, Decimal, None], default: Decimal = None) -> Decimal:
    """
    安全转换为 Decimal
    
    :param x: 输入值（支持 str/int/float/Decimal/None）
    :param default: 默认值（当 x 为 None/空字符串时使用）
    :return: Decimal 对象
    
    示例：
        to_dec('123.45') -> Decimal('123.45')
        to_dec(123.45) -> Decimal('123.45')
        to_dec(None, Decimal('0')) -> Decimal('0')
        to_dec('') -> Decimal('0')
        to_dec('-') -> Decimal('0')
    """
    if default is None:
        default = Decimal('0')
    
    if x is None:
        return default
    
    if isinstance(x, Decimal):
        return x
    
    if isinstance(x, (int, float)):
        # 从 float 转换时先转字符串避免精度丢失
        return Decimal(str(x))
    
    s = str(x).strip()
    if s == '' or s == '-':
        return default
    
    # 移除千分位逗号
    s = s.replace(',', '')
    
    try:
        return Decimal(s)
    except Exception:
        return default


def q_money(x: Union[Decimal, str, int, float], places: int = 2) -> Decimal:
    """
    金额舍入（保留2位小数，ROUND_HALF_UP）
    
    用于：amount, cost, value, pnl, fee, tax 等金额字段
    
    :param x: 输入值
    :param places: 小数位数（默认2）
    :return: 舍入后的 Decimal
    
    示例：
        q_money(Decimal('123.456')) -> Decimal('123.46')
        q_money(Decimal('123.454')) -> Decimal('123.45')
    """
    d = to_dec(x)
    quantizer = Decimal('0.1') ** places
    return d.quantize(quantizer, rounding=ROUND_HALF_UP)


def q_shares(x: Union[Decimal, str, int, float], places: int = 6) -> Decimal:
    """
    份额舍入（保留6位小数，可配置）
    
    用于：shares 字段
    
    :param x: 输入值
    :param places: 小数位数（默认6，与数据库 DECIMAL(20,6) 匹配）
    :return: 舍入后的 Decimal
    
    示例：
        q_shares(Decimal('123.456789012')) -> Decimal('123.456789')
    """
    d = to_dec(x)
    quantizer = Decimal('0.1') ** places
    return d.quantize(quantizer, rounding=ROUND_HALF_UP)


def q_nav(x: Union[Decimal, str, int, float], places: int = 4) -> Decimal:
    """
    净值舍入（保留4位小数，默认）
    
    用于：nav 字段（大部分基金净值）
    
    :param x: 输入值
    :param places: 小数位数（默认4）
    :return: 舍入后的 Decimal
    
    示例：
        q_nav(Decimal('1.234567')) -> Decimal('1.2346')
    """
    d = to_dec(x)
    quantizer = Decimal('0.1') ** places
    return d.quantize(quantizer, rounding=ROUND_HALF_UP)


def q_price(x: Union[Decimal, str, int, float], places: int = 4) -> Decimal:
    """
    价格舍入（保留4~6位小数，可配置）
    
    用于：price 字段（行情价格，根据行情源决定精度）
    
    :param x: 输入值
    :param places: 小数位数（默认4，可配置为6）
    :return: 舍入后的 Decimal
    """
    d = to_dec(x)
    quantizer = Decimal('0.1') ** places
    return d.quantize(quantizer, rounding=ROUND_HALF_UP)


def format_money(x: Union[Decimal, str, int, float], places: int = 2) -> str:
    """
    格式化金额为字符串（用于显示）
    
    :param x: 输入值
    :param places: 小数位数（默认2）
    :return: 格式化后的字符串
    
    示例：
        format_money(Decimal('123.45')) -> '123.45'
        format_money(Decimal('123.4')) -> '123.40'
    """
    d = q_money(x, places)
    return f"{d:.{places}f}"


def format_shares(x: Union[Decimal, str, int, float], places: int = 6) -> str:
    """
    格式化份额为字符串（用于显示）
    
    :param x: 输入值
    :param places: 小数位数（默认6）
    :return: 格式化后的字符串
    
    示例：
        format_shares(Decimal('123.456789')) -> '123.456789'
    """
    d = q_shares(x, places)
    return f"{d:.{places}f}"


def format_nav(x: Union[Decimal, str, int, float], places: int = 4) -> str:
    """
    格式化净值为字符串（用于显示）
    
    :param x: 输入值
    :param places: 小数位数（默认4）
    :return: 格式化后的字符串
    """
    d = q_nav(x, places)
    return f"{d:.{places}f}"


def safe_add(*args: Union[Decimal, str, int, float, None]) -> Decimal:
    """
    安全加法（处理 None）
    
    :param args: 多个数值
    :return: 求和结果
    
    示例：
        safe_add(Decimal('10'), Decimal('20'), None) -> Decimal('30')
    """
    result = Decimal('0')
    for x in args:
        result += to_dec(x)
    return result


def safe_sub(x: Union[Decimal, str, int, float, None], 
             y: Union[Decimal, str, int, float, None]) -> Decimal:
    """
    安全减法（处理 None）
    
    :param x: 被减数
    :param y: 减数
    :return: 差值
    """
    return to_dec(x) - to_dec(y)


def safe_mul(*args: Union[Decimal, str, int, float, None]) -> Decimal:
    """
    安全乘法（处理 None）
    
    :param args: 多个数值
    :return: 乘积结果
    """
    result = Decimal('1')
    for x in args:
        result *= to_dec(x, Decimal('1'))
    return result


def safe_div(x: Union[Decimal, str, int, float, None],
             y: Union[Decimal, str, int, float, None],
             default: Decimal = None) -> Decimal:
    """
    安全除法（处理 None 和除零）
    
    :param x: 被除数
    :param y: 除数
    :param default: 除零时的默认值（默认 Decimal('0')）
    :return: 商
    """
    if default is None:
        default = Decimal('0')
    
    x_dec = to_dec(x)
    y_dec = to_dec(y)
    
    if y_dec == 0:
        return default
    
    return x_dec / y_dec


def is_zero(x: Union[Decimal, str, int, float, None], 
            threshold: Decimal = None) -> bool:
    """
    判断是否为零（考虑舍入误差）
    
    :param x: 输入值
    :param threshold: 阈值（默认 Decimal('0.0001')）
    :return: 是否在阈值内视为零
    """
    if threshold is None:
        threshold = Decimal('0.0001')
    
    return abs(to_dec(x)) < threshold


def is_negative(x: Union[Decimal, str, int, float, None]) -> bool:
    """
    判断是否为负数
    
    :param x: 输入值
    :return: 是否为负数
    """
    return to_dec(x) < Decimal('0')

