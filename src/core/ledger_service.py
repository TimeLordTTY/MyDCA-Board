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

from data.data_store import (
    load_ledger, append_ledger, VALID_ENTRY_TYPES,
    format_decimal
)
from data.config_loader import load_accounts, load_categories


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
        写入的记录
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
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
        写入的记录
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
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
        写入的记录
    
    Raises:
        ValueError: 转出和转入账户相同
    """
    if account_from == account_to:
        raise ValueError("转出和转入账户不能相同")
    
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    record = {
        'event_time': event_time,
        'entry_type': 'transfer',
        'amount': format_decimal(amount, 2),
        'category_l1': '转账',
        'category_l2': '',
        'account_from': account_from,
        'account_to': account_to,
        'discount': '0',
        'reimbursable': '0',
        'note': note
    }
    
    append_ledger(record)
    return record


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
        写入的记录
    """
    if event_time is None:
        event_time = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    if refund_account is None:
        refund_account = original_expense.get('account_from', '')
    
    orig_cat = original_expense.get('category_l1', '')
    orig_note = original_expense.get('note', '')
    
    if note is None:
        note = f"退款: {orig_note}" if orig_note else "退款"
    
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
    return record


def list_recent_ledger(n: int = 20, entry_type: str = None) -> List[Dict]:
    """
    获取最近的账本记录
    
    Args:
        n: 返回条数
        entry_type: 筛选类型（expense/income/transfer）
    
    Returns:
        账本记录列表（按时间倒序）
    """
    ledger = load_ledger()
    
    if entry_type:
        ledger = [e for e in ledger if e.get('entry_type') == entry_type]
    
    return ledger[-n:] if len(ledger) > n else ledger


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

