"""
策略基类接口定义

所有策略都应继承 Strategy 基类，并实现 on_bar 方法
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, Any, TYPE_CHECKING

if TYPE_CHECKING:
    from ..engine.portfolio import Portfolio


@dataclass
class Context:
    """
    策略上下文
    
    引擎在每个交易日构造 Context 并传递给策略的 on_bar 方法
    
    Attributes:
        date: 当前日期
        nav: 当前净值
        portfolio: 组合对象（可以读取持仓、市值等信息）
        cash_inflow: 本周期新增的定投资金
        cash_pool: 当前可用于交易的现金池总额
        state: 策略自用状态字典，可读写
    """
    date: datetime
    nav: float
    portfolio: "Portfolio"
    cash_inflow: float
    cash_pool: float
    state: Dict[str, Any] = field(default_factory=dict)


@dataclass
class Signal:
    """
    交易信号
    
    策略在 on_bar 中返回 Signal，告诉引擎本日要进行的交易
    
    Attributes:
        buy_cash: 本周期打算用多少现金买入（不能超过 cash_pool）
        sell_units: 本周期打算卖出多少份额（不能超过持有份额）
        note: 备注，用于回测明细记录
    """
    buy_cash: float = 0.0
    sell_units: float = 0.0
    note: str = ""


class Strategy(ABC):
    """
    策略基类
    
    所有策略都应继承此类并实现 on_bar 方法
    
    设计原则：
    - 引擎负责构造 Context（含今日 nav、组合状态、现金流等）
    - 策略只负责根据 Context 决定本日买卖多少，并返回 Signal
    - 引擎绝对不包含任何策略判断逻辑
    
    Attributes:
        config: 策略配置字典
        state: 策略内部状态字典
    """
    
    def __init__(self, config: Dict[str, Any] = None):
        """
        初始化策略
        
        Args:
            config: 策略配置字典，由具体策略定义其内容
        """
        self.config = config or {}
        self.state: Dict[str, Any] = {}
    
    @abstractmethod
    def on_bar(self, ctx: Context) -> Signal:
        """
        处理每日行情
        
        每个交易日调用一次，策略根据当前上下文决定交易操作
        
        Args:
            ctx: 策略上下文，包含当日行情、组合状态、现金流等信息
        
        Returns:
            Signal: 交易信号，指示买入金额和/或卖出份额
        """
        pass
    
    def on_start(self) -> None:
        """
        回测开始时调用
        
        可选重写，用于初始化策略状态
        """
        pass
    
    def on_end(self) -> None:
        """
        回测结束时调用
        
        可选重写，用于清理或统计
        """
        pass
    
    def get_name(self) -> str:
        """
        获取策略名称
        
        Returns:
            策略类名
        """
        return self.__class__.__name__

