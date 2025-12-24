"""调度器服务 - 基于 APScheduler + job_config 表驱动"""
import logging
from datetime import datetime, timedelta
from typing import Dict, Optional

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    from apscheduler.triggers.cron import CronTrigger
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False
    logging.warning("APScheduler 未安装，调度功能不可用")

from data.db_connector import execute_query, execute_one, execute_update

logger = logging.getLogger(__name__)

_scheduler: Optional[BackgroundScheduler] = None


def get_job_configs(enabled_only: bool = True) -> list:
    """从数据库获取任务配置"""
    sql = "SELECT * FROM job_config WHERE 1=1"
    if enabled_only:
        sql += " AND enabled = 1"
    sql += " ORDER BY job_code"
    return execute_query(sql)


def update_job_status(job_code: str, status: str, message: str = None):
    """更新任务执行状态"""
    sql = """
        UPDATE job_config
        SET last_run_at = %s,
            last_status = %s,
            last_message = %s,
            updated_at = CURRENT_TIMESTAMP
        WHERE job_code = %s
    """
    execute_update(sql, (datetime.now(), status, message, job_code))


def run_rt_quote_job():
    """执行实时行情采集任务"""
    try:
        from core.market_quote_service import collect_realtime_quotes
        
        logger.info("开始执行实时行情采集任务")
        results = collect_realtime_quotes()
        
        success_count = sum(1 for v in results.values() if v)
        total_count = len(results)
        
        message = f"成功: {success_count}/{total_count}"
        update_job_status('rt_quote_1m', 'OK', message)
        logger.info(f"实时行情采集完成: {message}")
        
    except Exception as e:
        error_msg = str(e)
        update_job_status('rt_quote_1m', 'FAIL', error_msg)
        logger.error(f"实时行情采集失败: {e}", exc_info=True)


def run_otc_update_job():
    """执行场外净值更新任务"""
    try:
        from core.nav_collector import collect_and_store
        
        logger.info("开始执行场外净值更新任务")
        collect_and_store()
        
        update_job_status('otc_update_0800', 'OK', '场外净值更新完成')
        logger.info("场外净值更新完成")
        
    except Exception as e:
        error_msg = str(e)
        update_job_status('otc_update_0800', 'FAIL', error_msg)
        logger.error(f"场外净值更新失败: {e}", exc_info=True)


def start_scheduler():
    """启动调度器"""
    global _scheduler
    
    if not APSCHEDULER_AVAILABLE:
        logger.error("APScheduler 未安装，无法启动调度器")
        return False
    
    if _scheduler and _scheduler.running:
        logger.warning("调度器已在运行")
        return True
    
    try:
        _scheduler = BackgroundScheduler()
        
        # 从数据库加载任务配置
        jobs = get_job_configs(enabled_only=True)
        
        for job in jobs:
            job_code = job['job_code']
            cron_expr = job['cron_expr']
            
            # 解析 cron 表达式（简化版，支持标准格式）
            # 格式：分钟 小时 日 月 星期
            parts = cron_expr.split()
            if len(parts) != 5:
                logger.warning(f"无效的 cron 表达式: {job_code}={cron_expr}")
                continue
            
            minute, hour, day, month, day_of_week = parts
            
            # 创建触发器
            trigger = CronTrigger(
                minute=minute if minute != '*' else None,
                hour=hour if hour != '*' else None,
                day=day if day != '*' else None,
                month=month if month != '*' else None,
                day_of_week=day_of_week if day_of_week != '*' else None
            )
            
            # 根据任务代码选择执行函数
            if job_code == 'rt_quote_1m':
                func = run_rt_quote_job
            elif job_code.startswith('otc_update_'):
                func = run_otc_update_job
            else:
                logger.warning(f"未知任务代码: {job_code}")
                continue
            
            # 添加任务
            _scheduler.add_job(
                func=func,
                trigger=trigger,
                id=job_code,
                name=job_code,
                replace_existing=True
            )
            
            logger.info(f"添加任务: {job_code}, cron={cron_expr}")
        
        # 启动调度器
        _scheduler.start()
        logger.info("调度器已启动")
        return True
        
    except Exception as e:
        logger.error(f"启动调度器失败: {e}", exc_info=True)
        return False


def stop_scheduler():
    """停止调度器"""
    global _scheduler
    
    if _scheduler and _scheduler.running:
        _scheduler.shutdown()
        _scheduler = None
        logger.info("调度器已停止")
        return True
    
    return False


def is_scheduler_running() -> bool:
    """检查调度器是否运行"""
    return _scheduler is not None and _scheduler.running

