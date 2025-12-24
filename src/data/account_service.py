"""账户服务 - 从数据库读取账户配置"""
import logging
from typing import List, Dict, Optional

from data.db_connector import execute_query, execute_one, execute_update, execute_insert

logger = logging.getLogger(__name__)


def get_accounts(account_type: Optional[str] = None, is_active: bool = True) -> List[Dict]:
    """
    从数据库获取账户列表
    
    Args:
        account_type: 账户类型筛选
        is_active: 是否只返回启用账户
    
    Returns:
        账户列表
    """
    sql = """
        SELECT 
            id, account_code, account_id, account_name, account_type,
            parent_account_id, product_id, currency, is_active, note,
            created_at, updated_at
        FROM accounts
        WHERE 1=1
    """
    params = []
    
    if account_type:
        sql += " AND account_type = %s"
        params.append(account_type)
    
    if is_active:
        sql += " AND is_active = 1"
    
    sql += " ORDER BY account_code"
    
    return execute_query(sql, tuple(params))


def get_account_by_id(account_id: int) -> Optional[Dict]:
    """根据 account_id 获取账户"""
    sql = """
        SELECT 
            id, account_code, account_id, account_name, account_type,
            parent_account_id, product_id, currency, is_active, note,
            created_at, updated_at
        FROM accounts
        WHERE id = %s
    """
    return execute_one(sql, (account_id,))


def get_account_by_code(account_code: str) -> Optional[Dict]:
    """根据 account_code 获取账户（兼容旧接口）"""
    sql = """
        SELECT 
            id, account_code, account_id, account_name, account_type,
            parent_account_id, product_id, currency, is_active, note,
            created_at, updated_at
        FROM accounts
        WHERE account_code = %s
        LIMIT 1
    """
    return execute_one(sql, (account_code,))


def get_accounts_by_group(group_code: str) -> List[Dict]:
    """获取属于指定组的所有账户"""
    # 通过 account_groups 表关联查询
    sql = """
        SELECT 
            a.id, a.account_code, a.account_id, a.account_name, a.account_type,
            a.parent_account_id, a.product_id, a.currency, a.is_active, a.note
        FROM accounts a
        INNER JOIN account_groups ag ON ag.linked_product_id = a.product_id
        WHERE ag.group_code = %s AND a.is_active = 1
        ORDER BY a.account_code
    """
    return execute_query(sql, (group_code,))


def create_account(account_data: Dict) -> int:
    """创建新账户"""
    sql = """
        INSERT INTO accounts (
            account_code, account_id, account_name, account_type,
            parent_account_id, product_id, currency, note
        ) VALUES (
            %s, %s, %s, %s, %s, %s, %s, %s
        )
    """
    params = (
        account_data.get('account_code'),
        account_data.get('account_code'),  # account_id = account_code
        account_data.get('account_name'),
        account_data.get('account_type'),
        account_data.get('parent_account_id'),
        account_data.get('product_id'),
        account_data.get('currency', 'CNY'),
        account_data.get('note')
    )
    return execute_insert(sql, params)


def update_account(account_id: int, account_data: Dict) -> bool:
    """更新账户"""
    updates = []
    params = []
    
    for key in ['account_code', 'account_name', 'account_type', 
                'parent_account_id', 'product_id', 'currency', 'note', 'is_active']:
        if key in account_data:
            updates.append(f"{key} = %s")
            params.append(account_data[key])
    
    if not updates:
        return False
    
    # 如果更新 account_code，同时更新 account_id
    if 'account_code' in account_data:
        updates.append("account_id = %s")
        params.append(account_data['account_code'])
    
    params.append(account_id)
    
    sql = f"UPDATE accounts SET {', '.join(updates)} WHERE id = %s"
    execute_update(sql, tuple(params))
    return True


def delete_account(account_id: int) -> bool:
    """删除账户（软删除：设置 is_active=0）"""
    sql = "UPDATE accounts SET is_active = 0 WHERE id = %s"
    execute_update(sql, (account_id,))
    return True


def get_account_name(account_code: str) -> str:
    """获取账户名称（兼容旧接口）"""
    account = get_account_by_code(account_code)
    return account['account_name'] if account else account_code


# ============================================================
# 资金池规则相关函数
# ============================================================

def load_account_pool_rules(is_active: bool = True) -> List[Dict]:
    """加载资金池分配规则"""
    sql = """
        SELECT id, from_account_id, to_product_id, ratio, 
               min_amount, round_step, is_active,
               created_at, updated_at
        FROM account_pool_rules
        WHERE 1=1
    """
    params = []
    
    if is_active:
        sql += " AND is_active = 1"
    
    sql += " ORDER BY from_account_id, to_product_id"
    
    return execute_query(sql, tuple(params))


def get_account_pool_rule(rule_id: int) -> Optional[Dict]:
    """根据ID获取资金池规则"""
    sql = """
        SELECT id, from_account_id, to_product_id, ratio, 
               min_amount, round_step, is_active,
               created_at, updated_at
        FROM account_pool_rules
        WHERE id = %s
    """
    return execute_one(sql, (rule_id,))


def add_account_pool_rule(rule_data: Dict) -> int:
    """添加资金池规则"""
    sql = """
        INSERT INTO account_pool_rules (
            from_account_id, to_product_id, ratio, 
            min_amount, round_step, is_active
        ) VALUES (%s, %s, %s, %s, %s, %s)
    """
    params = (
        rule_data.get('from_account_id'),
        rule_data.get('to_product_id'),
        rule_data.get('ratio'),
        rule_data.get('min_amount', 0),
        rule_data.get('round_step', 1),
        rule_data.get('is_active', 1)
    )
    return execute_insert(sql, params)


def update_account_pool_rule(rule_id: int, rule_data: Dict) -> bool:
    """更新资金池规则"""
    updates = []
    params = []
    
    for key in ['from_account_id', 'to_product_id', 'ratio', 
                'min_amount', 'round_step', 'is_active']:
        if key in rule_data:
            updates.append(f"{key} = %s")
            params.append(rule_data[key])
    
    if not updates:
        return False
    
    params.append(rule_id)
    
    sql = f"UPDATE account_pool_rules SET {', '.join(updates)} WHERE id = %s"
    execute_update(sql, tuple(params))
    return True


def delete_account_pool_rule(rule_id: int) -> bool:
    """删除资金池规则（软删除：设置 is_active=0）"""
    sql = "UPDATE account_pool_rules SET is_active = 0 WHERE id = %s"
    execute_update(sql, (rule_id,))
    return True

