#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
账本业务服务层

提供账本（ledger.csv）的业务操作：
- 添加支出、收入、转账、退款
- 查询账本记录
- 校验账本数据

UI 和 CLI 都通过此服务操作账本，避免业务逻辑重复。
"""
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
import logging

from data.data_store import (
    load_ledger, append_ledger, VALID_ENTRY_TYPES,
    format_decimal
)
from data.config_loader import load_accounts, load_categories, get_project_root
from data.account_service import get_account_by_id

logger = logging.getLogger(__name__)


def get_account_parent_group(account_id: str) -> Optional[Dict]:
    """
    获取账户的父账户组信息（从数据库读取）
    
    Args:
        account_id: 账户ID（account_code）
    
    Returns:
        如果是子账户，返回父账户组信息 {'group_name': 'wenlibao', 'group_display': '稳利宝', 'accounts': [...]}
        否则返回 None
    """
    try:
        from data.account_service import get_account_by_code
        from data.db_connector import execute_query
        
        # 从数据库获取账户信息
        account = get_account_by_code(account_id)
        if not account:
            logger.debug(f"账户不存在: {account_id}")
            return None
        
        account_type = account.get('account_type', '')
        product_id = account.get('product_id')
        
        logger.debug(f"查找账户父组: account_id={account_id}, account_type={account_type}, product_id={product_id}")
        
        # 方法1: 通过 product_id 查找账户组（适用于 PRODUCT_SUB 和部分 CASH 账户）
        if product_id:
            # 查询账户组信息
            sql = """
                SELECT 
                    ag.group_code, ag.group_name, ag.linked_product_id
                FROM account_groups ag
                WHERE ag.linked_product_id = %s
                LIMIT 1
            """
            group_row = execute_query(sql, (product_id,))
            if group_row and len(group_row) > 0:
                group_info = group_row[0]
                group_code = group_info['group_code']
                group_name = group_info['group_name']
                
                # 获取该组的所有账户（通过 product_id 关联）
                accounts_sql = """
                    SELECT account_code
                    FROM accounts
                    WHERE product_id = %s AND is_active = 1
                    ORDER BY account_code
                """
                group_accounts_rows = execute_query(accounts_sql, (product_id,))
                group_accounts = [row['account_code'] for row in group_accounts_rows]
                
                logger.debug(f"找到账户组: group_code={group_code}, group_name={group_name}, accounts={group_accounts}")
                return {
                    'group_name': group_code,
                    'group_display': group_name,
                    'accounts': group_accounts
                }
        
        # 方法2: 如果是 CASH 类型账户且没有 product_id，检查父账户
        if account_type == 'CASH':
            parent_account_id = account.get('parent_account_id')
            if parent_account_id:
                # 查找父账户，看是否属于某个组
                parent_account = get_account_by_id(parent_account_id)
                if parent_account:
                    parent_product_id = parent_account.get('product_id')
                    if parent_product_id:
                        # 查询账户组信息
                        sql = """
                            SELECT 
                                ag.group_code, ag.group_name, ag.linked_product_id
                            FROM account_groups ag
                            WHERE ag.linked_product_id = %s
                            LIMIT 1
                        """
                        group_row = execute_query(sql, (parent_product_id,))
                        if group_row and len(group_row) > 0:
                            group_info = group_row[0]
                            group_code = group_info['group_code']
                            group_name = group_info['group_name']
                            
                            # 获取该组的所有账户
                            accounts_sql = """
                                SELECT account_code
                                FROM accounts
                                WHERE product_id = %s AND is_active = 1
                                ORDER BY account_code
                            """
                            group_accounts_rows = execute_query(accounts_sql, (parent_product_id,))
                            group_accounts = [row['account_code'] for row in group_accounts_rows]
                            
                            logger.debug(f"通过父账户找到账户组: group_code={group_code}, group_name={group_name}, accounts={group_accounts}")
                            return {
                                'group_name': group_code,
                                'group_display': group_name,
                                'accounts': group_accounts
                            }
        
        # 方法3: 通过账户代码前缀匹配查找账户组（适用于 ylb_life, ylb_finance 等）
        # 例如：ylb_life, ylb_finance 属于 ylb 组（账户代码以 ylb_ 开头）
        all_groups_sql = """
            SELECT 
                ag.group_code, ag.group_name, ag.linked_product_id
            FROM account_groups ag
        """
        all_groups = execute_query(all_groups_sql)
        
        for group_info in all_groups:
            group_code = group_info['group_code']
            group_name = group_info['group_name']
            group_linked_product_id = group_info['linked_product_id']
            
            # 检查账户代码是否以组代码开头（例如：ylb_life 以 ylb_ 开头）
            if group_code and account_id.startswith(f"{group_code}_"):
                # 获取该组的所有账户（通过账户代码前缀匹配）
                accounts_sql = """
                    SELECT account_code
                    FROM accounts
                    WHERE account_code LIKE %s AND is_active = 1
                    ORDER BY account_code
                """
                pattern = f"{group_code}_%"
                group_accounts_rows = execute_query(accounts_sql, (pattern,))
                group_accounts = [row['account_code'] for row in group_accounts_rows]
                
                if group_accounts:
                    logger.debug(f"通过代码前缀匹配找到账户组: group_code={group_code}, group_name={group_name}, accounts={group_accounts}")
                    return {
                        'group_name': group_code,
                        'group_display': group_name,
                        'accounts': group_accounts
                    }
            
            # 如果账户组有 linked_product_id，也检查是否匹配
            if group_linked_product_id and product_id == group_linked_product_id:
                # 获取该组的所有账户
                accounts_sql = """
                    SELECT account_code
                    FROM accounts
                    WHERE product_id = %s AND is_active = 1
                    ORDER BY account_code
                """
                group_accounts_rows = execute_query(accounts_sql, (group_linked_product_id,))
                group_accounts = [row['account_code'] for row in group_accounts_rows]
                
                if group_accounts:
                    logger.debug(f"通过 product_id 匹配找到账户组: group_code={group_code}, group_name={group_name}, accounts={group_accounts}")
                    return {
                        'group_name': group_code,
                        'group_display': group_name,
                        'accounts': group_accounts
                    }
        
        # 兼容旧逻辑：尝试从 JSON 文件读取（如果数据库中没有找到）
        import json
        accounts_path = get_project_root() / "config" / "accounts.json"
        if accounts_path.exists():
            with open(accounts_path, 'r', encoding='utf-8') as f:
                config = json.load(f)
            
            accounts = config.get('accounts', [])
            account_groups = config.get('account_groups', {})
            
            # 查找账户信息
            account_info = None
            for acc in accounts:
                if acc.get('id') == account_id:
                    account_info = acc
                    break
            
            if account_info:
                account_type_old = account_info.get('account_type', '')
                account_group = account_info.get('group', '')
                
                # product_sub 类型的账户有父账户组
                if account_type_old == 'product_sub' and account_group:
                    group_info = account_groups.get(account_group, {})
                    group_accounts = [acc['id'] for acc in accounts 
                                     if acc.get('group') == account_group]
                    return {
                        'group_name': account_group,
                        'group_display': group_info.get('name', account_group),
                        'accounts': group_accounts
                    }
                
                # 检查 cash 类型账户是否在组中
                if account_type_old == 'cash':
                    for group_name, group_info in account_groups.items():
                        if account_id in group_info.get('accounts', []):
                            return {
                                'group_name': group_name,
                                'group_display': group_info.get('name', group_name),
                                'accounts': group_info.get('accounts', [])
                            }
        
        return None
        
    except Exception as e:
        logger.error(f"获取账户父组信息失败: account_id={account_id}, 错误: {e}", exc_info=True)
        return None


def calc_account_balance(account_id: str, as_of_time: str = None, as_of_id: int = None) -> Decimal:
    """
    计算账户余额 = 初始余额 + Σ(入账) - Σ(出账)
    
    Args:
        account_id: 账户ID（可能是 account_code 或 account_id 字段值）
        as_of_time: 截止时间
        as_of_id: 截止记录ID（用于处理同一时间点的多条记录）
    
    Returns:
        账户余额（截止到指定时间和ID的记录，包含该记录）
    """
    from data.account_service import get_account_by_code, get_accounts
    
    # 获取账户信息，建立 account_code 和 account_id 的映射
    account = get_account_by_code(account_id)
    if not account:
        # 如果 account_id 不是 account_code，尝试通过 account_id 字段查找
        accounts = get_accounts(is_active=True)
        for acc in accounts:
            if str(acc.get('account_id', '')) == str(account_id):
                account = acc
                break
    
    if not account:
        logger.warning(f"账户不存在: {account_id}")
        return Decimal('0')
    
    account_code = account.get('account_code') or account.get('account_id', '')
    account_id_value = account.get('account_id')
    
    # 建立匹配值列表（account_code 和 account_id 都匹配）
    match_values = [account_code]
    if account_id_value:
        match_values.append(str(account_id_value))
    
    ledger = load_ledger()
    balance = Decimal('0')
    
    for record in ledger:
        event_time = record.get('event_time', '')
        record_id = record.get('id', 0)
        
        # 如果指定了截止条件，判断是否应该包含这条记录
        if as_of_time:
            # 时间更晚的跳过
            if event_time > as_of_time:
                continue
            # 时间相同但 ID 更大的跳过（同一时间点，按 ID 顺序）
            if event_time == as_of_time and as_of_id is not None and record_id > as_of_id:
                continue
        
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
        
        # 入账：income 且 account_to 匹配
        if entry_type == 'income' and account_to in match_values:
            balance += amount
        
        # 入账：transfer 且 account_to 匹配
        elif entry_type == 'transfer' and account_to in match_values:
            balance += amount
        
        # 出账：expense 且 account_from 匹配
        elif entry_type == 'expense' and account_from in match_values:
            balance -= amount
        
        # 出账：transfer 且 account_from 匹配
        elif entry_type == 'transfer' and account_from in match_values:
            balance -= amount
    
    return balance


def calc_group_balance(group_accounts: List[str], as_of_time: str = None, as_of_id: int = None) -> Decimal:
    """
    计算账户组的总余额
    
    Args:
        group_accounts: 组内账户ID列表
        as_of_time: 截止时间
        as_of_id: 截止记录ID
    
    Returns:
        组总余额
    """
    total = Decimal('0')
    for account_id in group_accounts:
        total += calc_account_balance(account_id, as_of_time, as_of_id)
    return total


@dataclass
class LedgerEntry:
    """账本记录"""
    event_time: str
    entry_type: str
    amount: Decimal
    category_l1: str
    category_l2: str = ''
    account_from: str = ''
    account_to: str = ''
    discount: Decimal = Decimal('0')
    reimbursable: bool = False
    note: str = ''


@dataclass
class ValidationResult:
    """校验结果"""
    success: bool
    errors: List[str]
    warnings: List[str]


def add_expense(
    account_from: str,
    amount: Decimal,
    category_l1: str,
    category_l2: str = '',
    event_time: str = None,
    note: str = '',
    discount: Decimal = Decimal('0'),
    reimbursable: bool = False
) -> Dict:
    """
    添加支出记录
    
    Args:
        account_from: 支出账户ID
        amount: 支出金额
        category_l1: 一级分类
        category_l2: 二级分类（可选）
        event_time: 时间（默认当前时间）
        note: 备注
        discount: 优惠金额
        reimbursable: 是否可报销
    
    Returns:
        写入的记录（包含支付后余额）
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    # 计算支付后余额（当前余额 - 本次支出）
    current_balance = calc_account_balance(account_from)
    balance_after = current_balance - amount
    
    # 检查是否有父账户组
    parent_group = get_account_parent_group(account_from)
    parent_balance_after = None
    if parent_group:
        parent_current = calc_group_balance(parent_group['accounts'])
        parent_balance_after = parent_current - amount
    
    record = {
        'event_time': event_time,
        'entry_type': 'expense',
        'amount': format_decimal(amount, 2),
        'category_l1': category_l1,
        'category_l2': category_l2,
        'account_from': account_from,
        'account_to': '',
        'discount': format_decimal(discount, 2) if discount > 0 else '0',
        'reimbursable': '1' if reimbursable else '0',
        'note': note
    }
    
    append_ledger(record)
    
    # 更新账户余额
    from data.account_service import update_account_balance
    update_account_balance(account_from, balance_after)
    
    # 返回时附上余额信息（不存储，仅用于显示）
    record['balance_after'] = format_decimal(balance_after, 2)
    record['parent_balance_after'] = format_decimal(parent_balance_after, 2) if parent_balance_after is not None else None
    return record


def add_income(
    account_to: str,
    amount: Decimal,
    category_l1: str,
    category_l2: str = '',
    event_time: str = None,
    note: str = ''
) -> Dict:
    """
    添加收入记录
    
    Args:
        account_to: 收入账户ID
        amount: 收入金额
        category_l1: 一级分类
        category_l2: 二级分类（可选）
        event_time: 时间（默认当前时间）
        note: 备注
    
    Returns:
        写入的记录（包含收入后余额）
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    # 计算收入后余额（当前余额 + 本次收入）
    current_balance = calc_account_balance(account_to)
    balance_after = current_balance + amount
    
    # 检查是否有父账户组
    parent_group = get_account_parent_group(account_to)
    parent_balance_after = None
    if parent_group:
        parent_current = calc_group_balance(parent_group['accounts'])
        parent_balance_after = parent_current + amount
    
    record = {
        'event_time': event_time,
        'entry_type': 'income',
        'amount': format_decimal(amount, 2),
        'category_l1': category_l1,
        'category_l2': category_l2,
        'account_from': '',
        'account_to': account_to,
        'discount': '0',
        'reimbursable': '0',
        'note': note
    }
    
    append_ledger(record)
    
    # 更新账户余额
    from data.account_service import update_account_balance
    update_account_balance(account_to, balance_after)
    
    # 返回时附上余额信息（不存储，仅用于显示）
    record['balance_after'] = format_decimal(balance_after, 2)
    record['parent_balance_after'] = format_decimal(parent_balance_after, 2) if parent_balance_after is not None else None
    return record


def add_transfer(
    account_from: str,
    account_to: str,
    amount: Decimal,
    event_time: str = None,
    note: str = ''
) -> Dict:
    """
    添加转账记录
    
    Args:
        account_from: 转出账户ID
        account_to: 转入账户ID
        amount: 转账金额
        event_time: 时间（默认当前时间）
        note: 备注
    
    Returns:
        写入的记录（包含转出账户余额）
    
    Raises:
        ValueError: 转出和转入账户相同
    """
    if account_from == account_to:
        raise ValueError("转出和转入账户不能相同")

    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    # 统一使用「一出一入」两条记录来表达转账：
    # 1) 源账户一条支出（expense）
    # 2) 目标账户一条收入（income）
    #
    # 这样 ledger 中的记录与「收入-支出」的余额计算公式完全一致，
    # 也更符合你在记账页面看到的逻辑。

    # 转出账户：支出
    expense_record = add_expense(
        account_from=account_from,
        amount=amount,
        category_l1="转账",
        category_l2="转出",
        event_time=event_time,
        note=note,
    )

    # 转入账户：收入
    add_income(
        account_to=account_to,
        amount=amount,
        category_l1="转账",
        category_l2="转入",
        event_time=event_time,
        note=note,
    )

    # 返回转出账户这条记录（包含 balance_after / parent_balance_after），
    # 供前端提示使用
    return expense_record


def add_refund(
    original_expense: Dict,
    refund_amount: Decimal,
    refund_account: str = None,
    event_time: str = None,
    note: str = None
) -> Dict:
    """
    添加退款记录
    
    Args:
        original_expense: 原支出记录
        refund_amount: 退款金额
        refund_account: 退款账户（默认原支出账户）
        event_time: 退款时间（默认当前时间）
        note: 备注（默认"退款: 原备注"）
    
    Returns:
        写入的记录（包含退款后余额）
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    if refund_account is None:
        refund_account = original_expense.get('account_from', '')
    
    orig_cat = original_expense.get('category_l1', '')
    orig_note = original_expense.get('note', '')
    
    if note is None:
        note = f"退款: {orig_note}" if orig_note else "退款"
    
    # 计算退款后余额（当前余额 + 退款金额）
    current_balance = calc_account_balance(refund_account)
    balance_after = current_balance + refund_amount
    
    # 检查是否有父账户组
    parent_group = get_account_parent_group(refund_account)
    parent_balance_after = None
    if parent_group:
        parent_current = calc_group_balance(parent_group['accounts'])
        parent_balance_after = parent_current + refund_amount
    
    record = {
        'event_time': event_time,
        'entry_type': 'income',
        'amount': format_decimal(refund_amount, 2),
        'category_l1': '退款',
        'category_l2': orig_cat,  # 用二级分类记录原分类
        'account_from': '',
        'account_to': refund_account,
        'discount': '0',
        'reimbursable': '0',
        'note': note
    }
    
    append_ledger(record)
    
    # 更新账户余额
    from data.account_service import update_account_balance
    update_account_balance(refund_account, balance_after)
    
    # 返回时附上余额信息（不存储，仅用于显示）
    record['balance_after'] = format_decimal(balance_after, 2)
    record['parent_balance_after'] = format_decimal(parent_balance_after, 2) if parent_balance_after is not None else None
    return record


def enrich_with_balances(records: List[Dict]) -> List[Dict]:
    """
    为记录列表动态计算余额（查询时计算，不存储）
    
    这个函数会为每条记录计算其操作后的账户余额和父账户余额。
    由于余额是动态计算的，修改任何历史记录后，后续所有余额会自动正确。
    
    Args:
        records: 账本记录列表（需要按时间+ID正序排列）
    
    Returns:
        带余额信息的记录列表
    """
    for record in records:
        event_time = record.get('event_time', '')
        record_id = record.get('id')  # 获取记录 ID
        entry_type = record.get('entry_type', '')
        
        # 确定主账户（支出/转账用 from，收入用 to）
        if entry_type in ['expense', 'transfer']:
            main_account = record.get('account_from', '')
        else:  # income
            main_account = record.get('account_to', '')
        
        if main_account:
            # 计算截止到该时间和ID的余额（包含当前记录）
            balance = calc_account_balance(main_account, event_time, record_id)
            record['balance_after'] = format_decimal(balance, 2)
            
            # 检查父账户组
            parent_group = get_account_parent_group(main_account)
            if parent_group:
                parent_balance = calc_group_balance(parent_group['accounts'], event_time, record_id)
                record['parent_balance_after'] = format_decimal(parent_balance, 2)
                logger.debug(f"计算父账户余额: account={main_account}, group={parent_group['group_name']}, balance={parent_balance}")
            else:
                record['parent_balance_after'] = None
                logger.debug(f"账户 {main_account} 没有父账户组")
        else:
            record['balance_after'] = None
            record['parent_balance_after'] = None
    
    return records


def list_recent_ledger(n: int = 20, entry_type: str = None, with_balances: bool = False) -> List[Dict]:
    """
    获取最近的账本记录（优化版，直接在数据库排序和限制）
    
    Args:
        n: 返回条数
        entry_type: 筛选类型（expense/income/transfer）
        with_balances: 是否计算余额（默认False，大量记录时较慢）
    
    Returns:
        账本记录列表（按时间倒序）
    """
    from data.data_store import load_recent_ledger
    records = load_recent_ledger(n, entry_type)
    
    if with_balances:
        # 需要按正序计算余额，然后再倒序返回
        records.reverse()
        records = enrich_with_balances(records)
        records.reverse()
    
    return records


def list_expenses(n: int = 20) -> List[Dict]:
    """
    获取最近的支出记录（用于退款选择）
    
    Args:
        n: 返回条数
    
    Returns:
        支出记录列表
    """
    return list_recent_ledger(n, entry_type='expense')


def get_all_ledger() -> List[Dict]:
    """获取所有账本记录"""
    return load_ledger()


def update_ledger_entry(record_id: int, record: Dict) -> bool:
    """
    更新账本记录
    
    Args:
        record_id: 记录 ID
        record: 更新后的记录数据
    
    Returns:
        是否更新成功
    """
    from data.data_store import update_ledger
    return update_ledger(record_id, record)


def validate_ledger() -> ValidationResult:
    """
    校验账本数据
    
    Returns:
        ValidationResult 包含错误和警告信息
    """
    errors = []
    warnings = []
    
    ledger = load_ledger()
    accounts = load_accounts()  # load_accounts() 直接返回账户列表
    account_ids = {acc['id'] for acc in accounts}
    
    for i, entry in enumerate(ledger, 1):
        entry_type = entry.get('entry_type', '')
        account_from = entry.get('account_from', '')
        account_to = entry.get('account_to', '')
        amount = entry.get('amount', '')
        
        # 检查 entry_type
        if entry_type not in VALID_ENTRY_TYPES:
            errors.append(f"第{i}行: entry_type '{entry_type}' 无效")
        
        # 检查金额
        try:
            amt = Decimal(amount)
            if amt < 0:
                errors.append(f"第{i}行: 金额 {amount} 为负数")
        except:
            errors.append(f"第{i}行: 金额 '{amount}' 格式错误")
        
        # 检查账户
        if entry_type == 'expense':
            if not account_from:
                errors.append(f"第{i}行: expense 缺少 account_from")
            elif account_from not in account_ids and account_from != 'other':
                warnings.append(f"第{i}行: account_from '{account_from}' 不在账户列表中")
        
        elif entry_type == 'income':
            if not account_to:
                errors.append(f"第{i}行: income 缺少 account_to")
            elif account_to not in account_ids and account_to != 'other':
                warnings.append(f"第{i}行: account_to '{account_to}' 不在账户列表中")
        
        elif entry_type == 'transfer':
            if not account_from or not account_to:
                errors.append(f"第{i}行: transfer 缺少账户信息")
            elif account_from == account_to:
                errors.append(f"第{i}行: transfer 的 account_from 和 account_to 相同")
    
    return ValidationResult(
        success=len(errors) == 0,
        errors=errors,
        warnings=warnings
    )


def get_account_options() -> List[Dict]:
    """
    获取账户选项列表（用于 UI 下拉框）
    
    Returns:
        账户列表 [{'id': 'ylb_life', 'name': '余利宝生活费'}, ...]
    """
    accounts = load_accounts()  # load_accounts() 直接返回账户列表
    return [{'id': acc['id'], 'name': acc['name']} for acc in accounts]


def get_category_options(entry_type: str) -> Dict[str, List[str]]:
    """
    获取分类选项（用于 UI 下拉框）
    
    Args:
        entry_type: expense 或 income
    
    Returns:
        分类字典 {'一级分类': ['二级分类1', '二级分类2'], ...}
    """
    categories = load_categories()
    
    if entry_type == 'transfer':
        return {'转账': []}
    
    return categories.get(entry_type, {})

