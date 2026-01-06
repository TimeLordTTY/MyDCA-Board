"""账户服务 - 从数据库读取账户配置"""
import logging
from typing import List, Dict, Optional
from decimal import Decimal

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
            balance, shares, created_at, updated_at
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
            balance, shares, created_at, updated_at
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
            balance, shares, created_at, updated_at
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
            a.parent_account_id, a.product_id, a.currency, a.is_active, a.note,
            a.balance, a.shares
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


def update_account_balance(account_code: str, balance: Decimal) -> bool:
    """更新账户余额"""
    from decimal import Decimal
    sql = "UPDATE accounts SET balance = %s WHERE account_code = %s"
    # 使用Decimal的字符串表示，避免精度丢失
    execute_update(sql, (str(balance), account_code))
    return True


def get_account_shares(account_code: str) -> Decimal:
    """
    获取账户份额
    
    Args:
        account_code: 账户代码
    
    Returns:
        账户份额，如果不存在则返回0
    """
    sql = "SELECT shares FROM accounts WHERE account_code = %s"
    result = execute_one(sql, (account_code,))
    if result and result.get('shares') is not None:
        return Decimal(str(result['shares']))
    return Decimal('0')


def update_account_shares(account_code: str, shares: Decimal, operation: str = 'set') -> bool:
    """
    更新账户份额，并自动更新 PRODUCT_SUB 账户的余额（余额 = 份额 × 净值）
    
    Args:
        account_code: 账户代码
        shares: 份额变化量（或新值）
        operation: 操作类型 'set'（设置）/'increase'（增加）/'decrease'（减少）
    
    Returns:
        是否更新成功
    """
    if operation == 'set':
        sql = "UPDATE accounts SET shares = %s WHERE account_code = %s"
        params = (str(shares), account_code)
    elif operation == 'increase':
        sql = "UPDATE accounts SET shares = COALESCE(shares, 0) + %s WHERE account_code = %s"
        params = (str(shares), account_code)
    elif operation == 'decrease':
        sql = "UPDATE accounts SET shares = GREATEST(COALESCE(shares, 0) - %s, 0) WHERE account_code = %s"
        params = (str(shares), account_code)
    else:
        logger.warning(f"未知的份额操作类型: {operation}")
        return False
    
    execute_update(sql, params)
    
    # 对于 PRODUCT_SUB 账户，自动更新余额（余额 = 份额 × 净值）
    _update_product_sub_account_balance(account_code)
    
    return True


def _update_product_sub_account_balance(account_code: str) -> None:
    """
    更新 PRODUCT_SUB 账户的余额（余额 = 份额 × 净值）
    
    内部函数，在份额更新后自动调用
    """
    from decimal import Decimal, ROUND_HALF_UP
    from data.product_service import get_product_by_id
    from data.nav_reader import get_latest_nav
    
    # 获取账户信息
    sql = "SELECT account_type, product_id, shares FROM accounts WHERE account_code = %s"
    result = execute_one(sql, (account_code,))
    
    if not result:
        return
    
    account_type = result.get('account_type')
    product_id = result.get('product_id')
    current_shares = Decimal(str(result.get('shares', 0))) if result.get('shares') else Decimal('0')
    
    # 只处理 PRODUCT_SUB 账户
    if account_type != 'PRODUCT_SUB' or not product_id:
        return
    
    # 获取产品代码
    product = get_product_by_id(product_id)
    if not product:
        logger.warning(f"账户 {account_code} 关联的产品 {product_id} 不存在")
        return
    
    product_code = product.get('code', '')
    if not product_code:
        return
    
    # 获取最新净值
    nav_data = get_latest_nav(product_code)
    if nav_data:
        nav = Decimal(str(nav_data[1]))
    else:
        # 如果没有净值数据，使用默认值 1.0
        nav = Decimal('1')
        logger.warning(f"产品 {product_code} 没有净值数据，使用默认值 1.0")
    
    # 计算并更新余额
    balance = (current_shares * nav).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    update_account_balance(account_code, balance)
    logger.debug(f"更新 PRODUCT_SUB 账户 {account_code} 余额: {current_shares} × {nav} = {balance}")


def get_all_account_shares_for_product(product_code: str) -> Dict[str, Decimal]:
    """
    获取产品下所有子账户的份额
    
    Args:
        product_code: 产品代码
    
    Returns:
        账户份额字典 {account_code: shares}
    """
    from data.product_service import get_product_by_code
    
    product = get_product_by_code(product_code)
    if not product or not product.get('id'):
        return {}
    
    product_id = product['id']
    sql = """
        SELECT account_code, shares
        FROM accounts
        WHERE product_id = %s AND account_type = 'PRODUCT_SUB' AND is_active = 1
    """
    results = execute_query(sql, (product_id,))
    
    shares_dict = {}
    for row in results:
        account_code = row.get('account_code')
        shares = row.get('shares')
        if account_code:
            shares_dict[account_code] = Decimal(str(shares)) if shares is not None else Decimal('0')
    
    return shares_dict


def recalculate_all_account_balances() -> Dict[str, Decimal]:
    """
    重新计算所有账户余额
    
    计算规则：
    - PRODUCT_SUB 类型账户（如稳利宝子账户）：余额 = 份额 × 净值
    - 其他类型账户（CASH、BUCKET 等）：余额 = 从 ledger 记录累计计算
    
    Returns:
        账户余额字典 {account_code: balance}
    """
    from decimal import Decimal
    from data.data_store import load_ledger
    from data.product_service import get_product_by_id
    from data.nav_reader import get_latest_nav
    
    # 从数据库获取所有账户（包含account_code）
    accounts_db = get_accounts(is_active=True)
    
    # 初始化余额字典
    balances = {}
    # 建立 account_code 到 account_id 的映射，以及反向映射
    code_to_id = {}  # {account_code: account_id}
    id_to_code = {}  # {account_id: account_code}
    # 记录 PRODUCT_SUB 类型账户及其关联的产品ID
    product_sub_accounts = {}  # {account_code: product_id}
    
    for account in accounts_db:
        account_code = account.get('account_code') or account.get('account_id', '')
        account_id_value = account.get('account_id')
        account_type = account.get('account_type', '')
        product_id = account.get('product_id')
        
        if account_code:
            balances[account_code] = Decimal('0')
            if account_id_value:
                account_id_str = str(account_id_value)
                code_to_id[account_code] = account_id_str
                id_to_code[account_id_str] = account_code
            
            # 记录 PRODUCT_SUB 类型账户
            if account_type == 'PRODUCT_SUB' and product_id:
                product_sub_accounts[account_code] = product_id
    
    # 对于 PRODUCT_SUB 类型账户，使用 份额 × 净值 计算余额
    for account_code, product_id in product_sub_accounts.items():
        # 获取账户份额
        shares = get_account_shares(account_code)
        if shares <= 0:
            balances[account_code] = Decimal('0')
            continue
        
        # 获取产品信息和最新净值
        product = get_product_by_id(product_id)
        if not product:
            logger.warning(f"账户 {account_code} 关联的产品 {product_id} 不存在")
            continue
        
        product_code = product.get('code', '')
        if not product_code:
            logger.warning(f"产品 {product_id} 没有代码")
            continue
        
        # 获取最新净值
        nav_data = get_latest_nav(product_code)
        if nav_data:
            nav = Decimal(str(nav_data[1]))
        else:
            # 如果没有净值数据，尝试使用 1.0（如稳利宝的初始净值）
            nav = Decimal('1')
            logger.warning(f"产品 {product_code} 没有净值数据，使用默认值 1.0")
        
        # 计算余额 = 份额 × 净值
        balance = shares * nav
        balances[account_code] = balance.quantize(Decimal('0.01'))
        logger.debug(f"PRODUCT_SUB 账户 {account_code}: 份额={shares}, 净值={nav}, 余额={balance}")
    
    # 对于非 PRODUCT_SUB 类型账户，使用 ledger 记录累计计算
    non_product_sub_accounts = set(balances.keys()) - set(product_sub_accounts.keys())
    
    # 遍历所有ledger记录，计算非PRODUCT_SUB账户的余额
    ledger = load_ledger()
    for record in ledger:
        entry_type = record.get('entry_type', '').lower()
        try:
            amount = Decimal(str(record.get('amount', '0')).replace(',', ''))
        except:
            amount = Decimal('0')
        
        account_from = str(record.get('account_from', '') or '')
        account_to = str(record.get('account_to', '') or '')
        
        # 清理账户代码：移除可能的 |fee_override: 等后缀（兼容旧数据）
        if '|fee_override' in account_from or 'fee_override' in account_from:
            account_from = account_from.split('|fee_override')[0].split('fee_override')[0].rstrip('|:').strip()
        if '|fee_override' in account_to or 'fee_override' in account_to:
            account_to = account_to.split('|fee_override')[0].split('fee_override')[0].rstrip('|:').strip()
        
        # 匹配 account_from：可能是 account_code 或 account_id
        matched_from_code = None
        if account_from in balances:
            matched_from_code = account_from
        elif account_from in id_to_code:
            matched_from_code = id_to_code[account_from]
        
        # 匹配 account_to：可能是 account_code 或 account_id
        matched_to_code = None
        if account_to in balances:
            matched_to_code = account_to
        elif account_to in id_to_code:
            matched_to_code = id_to_code[account_to]
        
        # 只处理非 PRODUCT_SUB 账户
        # 入账：income 且 account_to
        if entry_type == 'income' and matched_to_code and matched_to_code in non_product_sub_accounts:
            balances[matched_to_code] += amount
        
        # 入账：transfer 且 account_to
        elif entry_type == 'transfer' and matched_to_code and matched_to_code in non_product_sub_accounts:
            balances[matched_to_code] += amount
        
        # 出账：expense 且 account_from
        elif entry_type == 'expense' and matched_from_code and matched_from_code in non_product_sub_accounts:
            balances[matched_from_code] -= amount
        
        # 出账：transfer 且 account_from
        elif entry_type == 'transfer' and matched_from_code and matched_from_code in non_product_sub_accounts:
            balances[matched_from_code] -= amount
    
    # 更新数据库中的余额
    for account_code, balance in balances.items():
        update_account_balance(account_code, balance)
    
    logger.info(f"重新计算了 {len(balances)} 个账户的余额（其中 {len(product_sub_accounts)} 个 PRODUCT_SUB 账户按份额×净值计算）")
    return balances

