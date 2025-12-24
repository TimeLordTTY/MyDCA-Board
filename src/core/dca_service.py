"""定投计划服务"""
import logging
from datetime import date, timedelta
from decimal import Decimal
from typing import List, Dict, Optional

from data.db_connector import execute_query, execute_one, execute_insert, execute_update
from core.premium_brake import apply_premium_brake_old as apply_premium_brake
from core.pending_buy_service import add_pending_amount
from core.market_quote_service import get_latest_premium

logger = logging.getLogger(__name__)


def create_dca_plan(plan_data: Dict) -> int:
    """创建定投计划"""
    sql = """
        INSERT INTO dca_plan (
            product_id, from_account_id, weekday, amount, enabled
        ) VALUES (
            %s, %s, %s, %s, %s
        )
    """
    params = (
        plan_data.get('product_id'),
        plan_data.get('from_account_id'),
        plan_data.get('weekday'),
        str(plan_data.get('amount', 0)),
        plan_data.get('enabled', 1)
    )
    return execute_insert(sql, params)


def get_dca_plans(enabled_only: bool = True) -> List[Dict]:
    """获取定投计划列表"""
    sql = """
        SELECT 
            dp.id, dp.product_id, dp.from_account_id, dp.weekday, dp.amount, dp.enabled,
            p.code, p.product_name,
            a.account_code, a.account_name
        FROM dca_plan dp
        INNER JOIN products p ON dp.product_id = p.id
        INNER JOIN accounts a ON dp.from_account_id = a.id
        WHERE 1=1
    """
    if enabled_only:
        sql += " AND dp.enabled = 1"
    sql += " ORDER BY dp.id"
    return execute_query(sql)


def generate_dca_tasks(task_date: Optional[date] = None) -> Dict[str, int]:
    """
    生成定投任务（根据计划生成当日任务）
    
    Args:
        task_date: 任务日期，None 表示今天
    
    Returns:
        {created_count, updated_count} 字典
    """
    if task_date is None:
        task_date = date.today()
    
    # 获取今天是星期几
    weekday_map = {
        0: 'MON', 1: 'TUE', 2: 'WED', 3: 'THU',
        4: 'FRI', 5: 'SAT', 6: 'SUN'
    }
    today_weekday = weekday_map[task_date.weekday()]
    
    # 获取匹配的计划
    plans = get_dca_plans(enabled_only=True)
    matching_plans = [p for p in plans if p['weekday'] == today_weekday]
    
    created_count = 0
    updated_count = 0
    
    for plan in matching_plans:
        product_id = plan['product_id']
        from_account_id = plan['from_account_id']
        planned_amount = Decimal(str(plan['amount']))
        
        # 检查是否已存在任务
        existing = get_dca_task(task_date, product_id, from_account_id)
        
        # 获取溢价率（如果是 QDII）
        from data.product_service import get_product_by_id
        product = get_product_by_id(product_id)
        premium_rate = Decimal('0')
        if product and product.get('is_qdii'):
            premium_data = get_latest_premium(product_id)
            if premium_data:
                premium_rate = Decimal(str(premium_data.get('premium_rate', 0)))
        
        # 应用溢价刹车
        executed_amount, pending_amount = apply_premium_brake(premium_rate, planned_amount)
        
        if existing:
            # 更新现有任务
            update_dca_task(existing['id'], {
                'planned_amount': planned_amount,
                'premium_rate': premium_rate,
                'executed_amount': executed_amount,
                'pending_amount': pending_amount,
                'status': 'PENDING'
            })
            updated_count += 1
        else:
            # 创建新任务
            create_dca_task({
                'plan_id': plan['id'],
                'task_date': task_date,
                'product_id': product_id,
                'from_account_id': from_account_id,
                'planned_amount': planned_amount,
                'premium_rate': premium_rate,
                'executed_amount': executed_amount,
                'pending_amount': pending_amount,
                'status': 'PENDING'
            })
            created_count += 1
        
        # 如果有待买入金额，写入待买入池
        if pending_amount > 0:
            add_pending_amount(product_id, from_account_id, pending_amount, 
                             reason=f"溢价刹车: premium={premium_rate:.4%}")
    
    logger.info(f"生成定投任务完成: date={task_date}, created={created_count}, updated={updated_count}")
    
    return {
        'created_count': created_count,
        'updated_count': updated_count
    }


def create_dca_task(task_data: Dict) -> int:
    """创建定投任务"""
    sql = """
        INSERT INTO task_dca (
            plan_id, task_date, product_id, from_account_id,
            planned_amount, premium_rate, executed_amount, pending_amount, status, reason
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s, %s, %s
        )
    """
    params = (
        task_data.get('plan_id'),
        task_data.get('task_date'),
        task_data.get('product_id'),
        task_data.get('from_account_id'),
        str(task_data.get('planned_amount', 0)),
        str(task_data.get('premium_rate', 0)) if task_data.get('premium_rate') else None,
        str(task_data.get('executed_amount', 0)),
        str(task_data.get('pending_amount', 0)),
        task_data.get('status', 'PENDING'),
        task_data.get('reason')
    )
    return execute_insert(sql, params)


def get_dca_task(task_date: date, product_id: int, from_account_id: int) -> Optional[Dict]:
    """获取定投任务"""
    sql = """
        SELECT *
        FROM task_dca
        WHERE task_date = %s AND product_id = %s AND from_account_id = %s
    """
    return execute_one(sql, (task_date, product_id, from_account_id))


def update_dca_task(task_id: int, task_data: Dict) -> bool:
    """更新定投任务"""
    updates = []
    params = []
    
    for key in ['planned_amount', 'premium_rate', 'executed_amount', 
                'pending_amount', 'status', 'reason']:
        if key in task_data:
            updates.append(f"{key} = %s")
            if isinstance(task_data[key], Decimal):
                params.append(str(task_data[key]))
            else:
                params.append(task_data[key])
    
    if not updates:
        return False
    
    params.append(task_id)
    sql = f"UPDATE task_dca SET {', '.join(updates)}, updated_at = CURRENT_TIMESTAMP WHERE id = %s"
    execute_update(sql, tuple(params))
    return True


def reconcile_dca_tasks(task_date: Optional[date] = None) -> Dict[str, int]:
    """
    自动对账：对比 task_dca vs trade_fills
    
    Args:
        task_date: 对账日期，None 表示今天
    
    Returns:
        {matched_count, partial_count, miss_count} 字典
    """
    if task_date is None:
        task_date = date.today()
    
    # 获取当日所有任务
    sql = """
        SELECT *
        FROM task_dca
        WHERE task_date = %s AND status = 'PENDING'
    """
    tasks = execute_query(sql, (task_date,))
    
    matched_count = 0
    partial_count = 0
    miss_count = 0
    
    for task in tasks:
        product_id = task['product_id']
        planned_amount = Decimal(str(task['executed_amount']))  # 使用 executed_amount 作为计划金额
        
        # 查询当日实际成交（BUY）
        fill_sql = """
            SELECT SUM(amount) as total_amount, SUM(qty) as total_qty
            FROM trade_fills
            WHERE product_id = %s
              AND side = 'BUY'
              AND trade_date = %s
        """
        fill_result = execute_one(fill_sql, (product_id, task_date))
        
        if not fill_result or not fill_result.get('total_amount'):
            # 无成交
            update_dca_task(task['id'], {
                'status': 'MISS',
                'reason': '无成交导入'
            })
            miss_count += 1
            continue
        
        actual_amount = Decimal(str(fill_result['total_amount']))
        actual_qty = Decimal(str(fill_result.get('total_qty', 0)))
        
        # 判断对账状态（允许误差：金额 1 元内，或数量 0.01 内）
        amount_diff = abs(actual_amount - planned_amount)
        if amount_diff <= Decimal('1') or (actual_qty > 0 and abs(actual_qty - planned_amount / Decimal('100')) <= Decimal('0.01')):
            # 匹配
            update_dca_task(task['id'], {
                'status': 'MATCH',
                'reason': f'实际成交: {actual_amount}'
            })
            matched_count += 1
        elif actual_amount > 0:
            # 部分成交
            update_dca_task(task['id'], {
                'status': 'PARTIAL',
                'reason': f'部分成交: 计划={planned_amount}, 实际={actual_amount}'
            })
            partial_count += 1
        else:
            # 未成交
            update_dca_task(task['id'], {
                'status': 'MISS',
                'reason': '未成交'
            })
            miss_count += 1
    
    logger.info(f"对账完成: date={task_date}, matched={matched_count}, "
               f"partial={partial_count}, miss={miss_count}")
    
    return {
        'matched_count': matched_count,
        'partial_count': partial_count,
        'miss_count': miss_count
    }

