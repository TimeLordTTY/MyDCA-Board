"""
定时任务调度器
使用APScheduler实现定时数据采集和指标计算
"""
import sys
import os
from datetime import datetime, time
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger

# 添加父目录到路径
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from market.fund_collector import FundCollector
from market.etf_collector import ETFCollector
from indicator.calculator import IndicatorCalculator


class TaskScheduler:
    """任务调度器"""
    
    def __init__(self):
        self.scheduler = BlockingScheduler()
    
    def collect_fund_nav(self):
        """采集基金净值任务"""
        print(f"[{datetime.now()}] 开始采集基金净值...")
        try:
            collector = FundCollector()
            collector.collect_all()
            collector.close_db()
            print(f"[{datetime.now()}] 基金净值采集完成")
        except Exception as e:
            print(f"[{datetime.now()}] 基金净值采集失败: {e}")
    
    def collect_etf_realtime(self):
        """采集ETF实时行情任务"""
        print(f"[{datetime.now()}] 开始采集ETF实时行情...")
        try:
            collector = ETFCollector()
            collector.collect_realtime()
            collector.close_db()
            print(f"[{datetime.now()}] ETF实时行情采集完成")
        except Exception as e:
            print(f"[{datetime.now()}] ETF实时行情采集失败: {e}")
    
    def collect_etf_daily(self):
        """采集ETF日K线任务"""
        print(f"[{datetime.now()}] 开始采集ETF日K线...")
        try:
            collector = ETFCollector()
            collector.collect_daily_bars()
            collector.close_db()
            print(f"[{datetime.now()}] ETF日K线采集完成")
        except Exception as e:
            print(f"[{datetime.now()}] ETF日K线采集失败: {e}")
    
    def calculate_indicators(self):
        """计算指标任务"""
        print(f"[{datetime.now()}] 开始计算指标...")
        try:
            calculator = IndicatorCalculator()
            calculator.calculate_all_products()
            calculator.close_db()
            print(f"[{datetime.now()}] 指标计算完成")
        except Exception as e:
            print(f"[{datetime.now()}] 指标计算失败: {e}")
    
    def setup_jobs(self):
        """设置定时任务"""
        # 基金净值采集：每天18:00（T+1日净值通常在18:00更新）
        self.scheduler.add_job(
            self.collect_fund_nav,
            trigger=CronTrigger(hour=18, minute=0),
            id='collect_fund_nav',
            name='采集基金净值',
            replace_existing=True
        )
        
        # ETF实时行情：交易时间内每5分钟采集一次（9:30-15:00）
        self.scheduler.add_job(
            self.collect_etf_realtime,
            trigger=CronTrigger(minute='*/5', hour='9-15'),
            id='collect_etf_realtime',
            name='采集ETF实时行情',
            replace_existing=True
        )
        
        # ETF日K线：每天15:30采集（收盘后）
        self.scheduler.add_job(
            self.collect_etf_daily,
            trigger=CronTrigger(hour=15, minute=30),
            id='collect_etf_daily',
            name='采集ETF日K线',
            replace_existing=True
        )
        
        # 指标计算：每天16:00计算（数据采集完成后）
        self.scheduler.add_job(
            self.calculate_indicators,
            trigger=CronTrigger(hour=16, minute=0),
            id='calculate_indicators',
            name='计算指标',
            replace_existing=True
        )
        
        print("定时任务已设置:")
        print("  - 基金净值采集: 每天 18:00")
        print("  - ETF实时行情: 交易时间内每5分钟")
        print("  - ETF日K线: 每天 15:30")
        print("  - 指标计算: 每天 16:00")
    
    def start(self):
        """启动调度器"""
        self.setup_jobs()
        print(f"[{datetime.now()}] 任务调度器启动")
        try:
            self.scheduler.start()
        except (KeyboardInterrupt, SystemExit):
            print(f"[{datetime.now()}] 任务调度器停止")


if __name__ == '__main__':
    scheduler = TaskScheduler()
    scheduler.start()
