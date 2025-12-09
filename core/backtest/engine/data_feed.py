"""
行情数据读取与迭代

DataFeed 负责将历史净值数据逐条提供给回测引擎
"""

from typing import List, Iterator
from .types import NavBar


class DataFeed:
    """
    行情数据源
    
    将基金净值数据封装为可迭代对象，按时间顺序逐条返回
    
    Attributes:
        bars: 净值数据列表（应按日期升序排列）
    """
    
    def __init__(self, bars: List[NavBar]):
        """
        初始化数据源
        
        Args:
            bars: NavBar 列表，应按日期从早到晚排序
        """
        self.bars = bars
        self._index = 0
    
    def __iter__(self) -> Iterator[NavBar]:
        """返回迭代器自身"""
        self._index = 0
        return self
    
    def __next__(self) -> NavBar:
        """
        返回下一条净值数据
        
        Raises:
            StopIteration: 当所有数据已遍历完
        """
        if self._index >= len(self.bars):
            raise StopIteration
        bar = self.bars[self._index]
        self._index += 1
        return bar
    
    def __len__(self) -> int:
        """返回数据条数"""
        return len(self.bars)
    
    def reset(self) -> None:
        """重置迭代器到起始位置"""
        self._index = 0
    
    @property
    def start_date(self):
        """返回数据起始日期"""
        return self.bars[0].date if self.bars else None
    
    @property
    def end_date(self):
        """返回数据结束日期"""
        return self.bars[-1].date if self.bars else None

