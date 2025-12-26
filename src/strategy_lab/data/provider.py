# -*- coding: utf-8 -*-
"""
DataProvider - 统一数据接口

支持从数据库和 AKShare 读取日K数据，统一转换为 DailyBar 格式。
"""

from datetime import date
from typing import List, Optional
import logging

from .daily_bar import DailyBar
import sys
from pathlib import Path
# 添加项目根目录到路径（使相对导入生效）
project_root = Path(__file__).parent.parent.parent.parent
if str(project_root) not in sys.path:
    sys.path.insert(0, str(project_root))
from data.db_connector import execute_query
from data.product_service import get_product_by_id
from core.market_quote_service import get_daily_bars as get_market_bars
from adaptor.akshare_client import fetch_daily_bar as akshare_fetch_daily_bar
from core.market_quote_service import save_daily_bar

logger = logging.getLogger(__name__)


class DataProvider:
    """
    统一数据提供者
    
    根据产品 channel 自动选择数据源：
    - EXCHANGE: 从 market_bar_d 表读取
    - OTC: 从 nav 表读取（转换为 DailyBar）
    """
    
    def __init__(self, auto_fetch: bool = True):
        """
        初始化数据提供者
        
        Args:
            auto_fetch: 如果数据库中没有数据，是否自动从 AKShare 拉取并落库
        """
        self.auto_fetch = auto_fetch
    
    def get_bars(
        self, 
        product_id: int, 
        start: date, 
        end: date
    ) -> List[DailyBar]:
        """
        获取日K数据
        
        Args:
            product_id: 产品ID
            start: 开始日期
            end: 结束日期
        
        Returns:
            DailyBar 列表，按日期升序排列
        """
        # 获取产品信息
        product = get_product_by_id(product_id)
        if not product:
            logger.error(f"产品不存在: product_id={product_id}")
            return []
        
        channel = product.get('channel', 'OTC')
        product_code = product.get('code', '')
        market = product.get('market', 'NA')
        
        if channel == 'EXCHANGE':
            # 场内：从 market_bar_d 表读取
            bars = self._get_from_database(product_id, start, end)
            
            # 如果数据库中没有数据且允许自动拉取，则从 AKShare 拉取
            if not bars and self.auto_fetch:
                bars = self._fetch_from_akshare(product_id, product_code, market, start, end)
        else:
            # 场外：从 nav 表读取，转换为 DailyBar
            bars = self._get_from_nav(product_id, product_code, start, end)
        
        # 按日期排序
        bars.sort(key=lambda x: x.date)
        return bars
    
    def _get_from_database(
        self, 
        product_id: int, 
        start: date, 
        end: date
    ) -> List[DailyBar]:
        """从 market_bar_d 表读取"""
        bars_data = get_market_bars(product_id, start.strftime('%Y-%m-%d'), end.strftime('%Y-%m-%d'))
        
        bars = []
        for row in bars_data:
            try:
                bar = DailyBar(
                    date=row['trade_date'] if isinstance(row['trade_date'], date) else date.fromisoformat(str(row['trade_date'])),
                    open=float(row.get('open', 0) or 0),
                    high=float(row.get('high', 0) or 0),
                    low=float(row.get('low', 0) or 0),
                    close=float(row.get('close', 0) or 0),
                    volume=float(row.get('volume', 0)) if row.get('volume') else None
                )
                bars.append(bar)
            except Exception as e:
                logger.warning(f"解析日K数据失败: {row}, error={e}")
                continue
        
        return bars
    
    def _fetch_from_akshare(
        self,
        product_id: int,
        product_code: str,
        market: str,
        start: date,
        end: date
    ) -> List[DailyBar]:
        """从 AKShare 拉取并落库"""
        logger.info(f"从 AKShare 拉取日K数据: product_code={product_code}, market={market}, start={start}, end={end}")
        
        # 调用 AKShare 接口
        bars_data = akshare_fetch_daily_bar(
            product_code, 
            market, 
            start.strftime('%Y%m%d'), 
            end.strftime('%Y%m%d')
        )
        
        bars = []
        for row in bars_data:
            try:
                # 解析日期
                trade_date = date.fromisoformat(str(row['trade_date']))
                
                bar = DailyBar(
                    date=trade_date,
                    open=float(row.get('open', 0) or 0),
                    high=float(row.get('high', 0) or 0),
                    low=float(row.get('low', 0) or 0),
                    close=float(row.get('close', 0) or 0),
                    volume=float(row.get('volume', 0)) if row.get('volume') else None
                )
                bars.append(bar)
                
                # 落库
                bar_data = {
                    'trade_date': trade_date,
                    'open': bar.open,
                    'high': bar.high,
                    'low': bar.low,
                    'close': bar.close,
                    'volume': bar.volume,
                    'amount': None,
                    'prev_close': None
                }
                save_daily_bar(product_id, bar_data, source='AKSHARE')
                
            except Exception as e:
                logger.warning(f"解析 AKShare 日K数据失败: {row}, error={e}")
                continue
        
        logger.info(f"从 AKShare 拉取完成，共 {len(bars)} 条数据")
        return bars
    
    def _get_from_nav(
        self,
        product_id: int,
        product_code: str,
        start: date,
        end: date
    ) -> List[DailyBar]:
        """从 nav 表读取，转换为 DailyBar"""
        # 从数据库查询指定日期范围的净值
        sql = """
            SELECT nav_date, nav
            FROM nav
            WHERE product_code = %s 
                AND nav_date >= %s 
                AND nav_date <= %s
            ORDER BY nav_date ASC
        """
        nav_records = execute_query(sql, (product_code, start, end))
        
        bars = []
        for nav_record in nav_records:
            try:
                nav_date = nav_record['nav_date']
                if isinstance(nav_date, str):
                    nav_date = date.fromisoformat(nav_date)
                elif not isinstance(nav_date, date):
                    nav_date = date.fromisoformat(str(nav_date))
                
                # 只处理日期范围内的数据
                if nav_date < start or nav_date > end:
                    continue
                
                nav_value = float(nav_record.get('nav', 0) or 0)
                if nav_value <= 0:
                    continue
                
                # 场外基金只有净值，用净值作为 close，其他价格字段也用净值
                bar = DailyBar(
                    date=nav_date,
                    open=nav_value,
                    high=nav_value,
                    low=nav_value,
                    close=nav_value,
                    volume=None
                )
                bars.append(bar)
            except Exception as e:
                logger.warning(f"解析净值数据失败: {nav_record}, error={e}")
                continue
        
        return bars

