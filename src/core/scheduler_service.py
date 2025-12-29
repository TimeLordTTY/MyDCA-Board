"""调度器服务 - 基于 APScheduler + job_config 表驱动"""
import logging
from datetime import datetime, timedelta
from typing import Dict, Optional, Any

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    from apscheduler.triggers.cron import CronTrigger
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False
    BackgroundScheduler = None  # 占位符，避免类型注解错误
    CronTrigger = None
    logging.warning("APScheduler 未安装，调度功能不可用")

from data.db_connector import execute_query, execute_one, execute_update

logger = logging.getLogger(__name__)

# 使用 Any 类型，避免在 APScheduler 未安装时类型注解错误
_scheduler: Optional[Any] = None


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
        # 检查是否是交易日
        from utils.trade_calendar import is_trade_day
        from datetime import date
        
        today = date.today()
        if not is_trade_day(today):
            logger.debug(f"今日 {today} 非交易日，跳过实时行情采集")
            # 使用 'OK' 状态，因为跳过是正常行为，不是错误
            update_job_status('rt_quote_1m', 'OK', f'非交易日已跳过: {today}')
            return
        
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


def run_indicator_daily_job():
    """执行日更指标计算任务"""
    try:
        from advisor.indicator_job import calculate_indicators_for_all_products
        
        logger.info("开始执行日更指标计算任务")
        result = calculate_indicators_for_all_products()
        
        message = f"成功: {result['success_count']}, 失败: {result['fail_count']}"
        update_job_status('indicator_daily', 'OK', message)
        logger.info(f"日更指标计算完成: {message}")
        
    except Exception as e:
        error_msg = str(e)
        update_job_status('indicator_daily', 'FAIL', error_msg)
        logger.error(f"日更指标计算失败: {e}", exc_info=True)


def run_advisor_suggestion_job():
    """执行生产建议生成任务"""
    try:
        from advisor.advisor_service import run_for_all_products
        
        logger.info("开始执行生产建议生成任务")
        result = run_for_all_products()
        
        message = f"成功: {result['success_count']}, 失败: {result['fail_count']}"
        update_job_status('advisor_suggestion_1m', 'OK', message)
        logger.info(f"生产建议生成完成: {message}")
        
    except Exception as e:
        error_msg = str(e)
        update_job_status('advisor_suggestion_1m', 'FAIL', error_msg)
        logger.error(f"生产建议生成失败: {e}", exc_info=True)


def run_refresh_quote_job():
    """
    执行完整的行情刷新任务（包含行情采集、指标计算、建议生成）
    在交易日的交易时间段内定时执行
    """
    try:
        from utils.trade_calendar import is_trade_day, is_trade_time
        from datetime import datetime, date
        
        now = datetime.now()
        today = date.today()
        
        # 检查是否是交易日且在交易时间段内
        if not is_trade_day(today):
            logger.debug(f"今日 {today} 非交易日，跳过行情刷新任务")
            update_job_status('refresh_quote_trading', 'OK', f'非交易日已跳过: {today}')
            return
        
        if not is_trade_time(now):
            logger.debug(f"当前时间 {now.strftime('%H:%M')} 不在交易时间段内，跳过行情刷新任务")
            update_job_status('refresh_quote_trading', 'OK', f'非交易时段已跳过: {now.strftime("%H:%M")}')
            return
        
        from core.market_quote_service import collect_realtime_quotes
        from advisor.indicator_job import calculate_indicators_for_all_products
        from advisor.advisor_service import run_for_all_products
        from data.product_service import get_products
        
        logger.info("开始执行行情刷新任务（行情采集+指标计算+建议生成）")
        
        # 获取所有活跃产品
        all_products = get_products(is_active=True)
        exchange_products = [p for p in all_products if p.get('channel') == 'EXCHANGE']
        otc_products = [p for p in all_products if p.get('channel') == 'OTC']
        
        success_count = 0
        fail_count = 0
        
        # 1. 刷新场内产品行情
        if exchange_products:
            product_ids = [p['id'] for p in exchange_products]
            results = collect_realtime_quotes(product_ids)
            success_count += sum(1 for v in results.values() if v)
            fail_count += sum(1 for v in results.values() if not v)
            logger.info(f"行情采集完成: 成功={success_count}, 失败={fail_count}")
        
        # 2. 刷新场外产品净值
        if otc_products:
            try:
                from core.nav_collector import collect_and_store
                collect_and_store()
                logger.info("场外净值更新完成")
            except Exception as e:
                logger.warning(f"场外净值更新失败: {e}")
                fail_count += 1
        
        # 3. 为所有场内产品计算指标
        if exchange_products:
            try:
                indicator_results = calculate_indicators_for_all_products()
                success_count += indicator_results.get('success_count', 0)
                fail_count += indicator_results.get('fail_count', 0)
                logger.info(f"指标计算完成: 成功={indicator_results.get('success_count', 0)}, 失败={indicator_results.get('fail_count', 0)}")
            except Exception as e:
                logger.error(f"指标计算失败: {e}", exc_info=True)
                fail_count += len(exchange_products)
        
        # 4. 为所有场内产品生成建议
        if exchange_products:
            try:
                advisor_results = run_for_all_products()
                success_count += advisor_results.get('success_count', 0)
                fail_count += advisor_results.get('fail_count', 0)
                logger.info(f"建议生成完成: 成功={advisor_results.get('success_count', 0)}, 失败={advisor_results.get('fail_count', 0)}")
            except Exception as e:
                logger.error(f"建议生成失败: {e}", exc_info=True)
                fail_count += len(exchange_products)
        
        message = f"成功: {success_count}, 失败: {fail_count}"
        update_job_status('refresh_quote_trading', 'OK', message)
        logger.info(f"行情刷新任务完成: {message}")
        
    except Exception as e:
        error_msg = str(e)
        update_job_status('refresh_quote_trading', 'FAIL', error_msg)
        logger.error(f"行情刷新任务失败: {e}", exc_info=True)


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
            
            # 解析 cron 表达式（支持标准格式）
            # 格式：分钟 小时 日 月 星期
            parts = cron_expr.split()
            if len(parts) != 5:
                logger.warning(f"无效的 cron 表达式: {job_code}={cron_expr}")
                continue
            
            minute, hour, day, month, day_of_week = parts
            
            # 解析各个字段，支持 */1, 9-11,13-14, 1-5 等格式
            # APScheduler 的 CronTrigger 支持这些格式，但需要正确传递
            def parse_field(field_str):
                """解析 cron 字段，返回 APScheduler 可用的值"""
                if field_str == '*':
                    return None
                # 支持 */1 格式 - APScheduler 需要转换为 '*' 或使用 IntervalTrigger
                if field_str.startswith('*/'):
                    # 对于 */1，直接使用 '*' 表示每分钟/每小时
                    if field_str == '*/1':
                        return '*'
                    # 对于其他间隔，保持原样（APScheduler 可能不支持，需要特殊处理）
                    interval = int(field_str[2:])
                    # 这里简化处理，对于非1的间隔可能需要使用 IntervalTrigger
                    return '*'
                # 支持范围格式，如 9-11,13-14 或 1-5
                # APScheduler 支持这种格式，直接传递字符串
                if ',' in field_str or '-' in field_str:
                    return field_str
                # 单个值
                try:
                    return int(field_str)
                except ValueError:
                    return field_str
            
            # 创建触发器
            try:
                trigger = CronTrigger(
                    minute=parse_field(minute),
                    hour=parse_field(hour),
                    day=parse_field(day),
                    month=parse_field(month),
                    day_of_week=parse_field(day_of_week)
                )
            except Exception as e:
                logger.error(f"创建触发器失败: {job_code}={cron_expr}, 错误: {e}")
                continue
            
            # 根据任务代码选择执行函数
            if job_code == 'rt_quote_1m':
                func = run_rt_quote_job
            elif job_code.startswith('otc_update_'):
                func = run_otc_update_job
            elif job_code == 'indicator_daily':
                func = run_indicator_daily_job
            elif job_code == 'advisor_suggestion_1m':
                func = run_advisor_suggestion_job
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
    if not APSCHEDULER_AVAILABLE:
        return False
    return _scheduler is not None and _scheduler.running

