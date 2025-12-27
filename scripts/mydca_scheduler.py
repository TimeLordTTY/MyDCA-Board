#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
MyDCA-Board 调度器独立进程

用法：
    python scripts/mydca_scheduler.py

功能：
    - 从 job_config 表读取任务配置
    - 使用 APScheduler 执行定时任务
    - 实时行情采集（每分钟）
    - 场外净值更新（每天 08:00/14:00/22:00）
"""
import sys
import logging
import signal
from pathlib import Path

# 添加 src 目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from core.scheduler_service import start_scheduler, stop_scheduler, is_scheduler_running

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def signal_handler(sig, frame):
    """信号处理（优雅退出）"""
    logger.info("收到退出信号，正在停止调度器...")
    stop_scheduler()
    sys.exit(0)


def main():
    """主函数"""
    # 注册信号处理
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    logger.info("=" * 50)
    logger.info("MyDCA-Board 调度器启动")
    logger.info("=" * 50)
    
    # 启动调度器
    if not start_scheduler():
        logger.error("调度器启动失败")
        sys.exit(1)
    
    logger.info("调度器运行中，按 Ctrl+C 退出...")
    
    # 保持运行
    try:
        while is_scheduler_running():
            import time
            time.sleep(1)
    except KeyboardInterrupt:
        logger.info("收到中断信号")
    finally:
        stop_scheduler()
        logger.info("调度器已退出")


if __name__ == "__main__":
    main()


