#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
校验和补全交易流水

功能：
  1. 净值校验：如果 nav 为空，从 nav 文件获取；如果非空，校验是否正确
  2. 金额校验：份额 × 净值 与 金额 + 手续费 是否一致

用法：
  python validate_transactions.py [--fix]

参数：
  --fix: 自动修复（填补空净值，修正错误净值）。不加此参数则只检查不修改。

示例：
  python validate_transactions.py         # 只检查，不修改
  python validate_transactions.py --fix   # 检查并修复
"""

import sys
import csv
import json
from pathlib import Path
from decimal import Decimal, ROUND_HALF_UP

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from config_loader import get_project_root


def safe_decimal(value, default=Decimal('0')) -> Decimal:
    """安全地将值转换为Decimal"""
    if value is None:
        return default
    s = str(value).strip()
    if s == '' or s == '-':
        return default
    try:
        return Decimal(s)
    except:
        return default


def load_products_map():
    """加载产品代码到名称的映射"""
    products_path = get_project_root() / "config" / "products.json"
    products_map = {}
    if products_path.exists():
        with open(products_path, 'r', encoding='utf-8') as f:
            products = json.load(f)
            for p in products:
                products_map[p['id']] = p['name']
    return products_map


def load_nav_data(product_code, product_name):
    """
    加载某产品的净值数据
    :return: {ISS_DATE: NAV}
    """
    nav_dir = get_project_root() / "data" / "nav"
    nav_path = nav_dir / f"{product_code}_{product_name}.csv"
    
    if not nav_path.exists():
        return {}
    
    nav_map = {}
    with open(nav_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            date = row.get('ISS_DATE', '').strip()
            nav = row.get('NAV', '').strip()
            if date and nav:
                nav_map[date] = nav
    return nav_map


def validate_transactions(fix_mode=False):
    """
    校验交易流水
    :param fix_mode: 是否修复模式
    """
    products_map = load_products_map()
    transactions_path = get_project_root() / "data" / "transactions.csv"
    
    if not transactions_path.exists():
        print("错误: transactions.csv 不存在")
        return
    
    # 读取所有交易记录
    transactions = []
    with open(transactions_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        for row in reader:
            transactions.append(row)
    
    # 缓存每个产品的净值数据
    nav_cache = {}
    
    # 统计 - 净值校验
    empty_nav_count = 0
    nav_mismatch_count = 0
    nav_fixed_count = 0
    nav_missing_count = 0
    
    # 统计 - 金额校验
    amount_mismatch_count = 0
    amount_mismatch_list = []
    
    # ========== 第一部分：净值校验 ==========
    print("=" * 80)
    print("【1】净值校验")
    print("=" * 80)
    
    for i, txn in enumerate(transactions):
        product_code = txn.get('product_code', '').strip()
        nav_date = txn.get('nav_date', '').strip()
        nav_value = txn.get('nav', '').strip()
        action = txn.get('action', '').strip().upper()
        
        # 分红没有净值，跳过
        if action == 'DIVIDEND':
            continue
        
        if not product_code or not nav_date:
            continue
        
        # 获取产品名称
        product_name = products_map.get(product_code, product_code)
        
        # 加载该产品的净值数据（带缓存）
        if product_code not in nav_cache:
            nav_cache[product_code] = load_nav_data(product_code, product_name)
        
        nav_data = nav_cache[product_code]
        expected_nav = nav_data.get(nav_date, None)
        
        # 判断 nav 是否为空
        is_empty = not nav_value or nav_value == '-' or nav_value == '0'
        
        if is_empty:
            empty_nav_count += 1
            if expected_nav:
                print(f"[空净值] {txn['date']} {product_code} nav_date={nav_date}")
                print(f"         -> 从nav文件获取: {expected_nav}")
                if fix_mode:
                    transactions[i]['nav'] = expected_nav
                    nav_fixed_count += 1
            else:
                print(f"[空净值] {txn['date']} {product_code} nav_date={nav_date}")
                print(f"         -> nav文件中无此日期数据!")
                nav_missing_count += 1
        else:
            # 校验净值是否一致
            if expected_nav:
                try:
                    txn_nav = Decimal(nav_value)
                    exp_nav = Decimal(expected_nav)
                    if abs(txn_nav - exp_nav) > Decimal('0.0001'):
                        nav_mismatch_count += 1
                        print(f"[净值不匹配] {txn['date']} {product_code} nav_date={nav_date}")
                        print(f"             流水: {nav_value}, nav文件: {expected_nav}")
                        if fix_mode:
                            transactions[i]['nav'] = expected_nav
                            nav_fixed_count += 1
                            print(f"             -> 已修正为: {expected_nav}")
                except Exception as e:
                    print(f"[解析错误] {txn['date']} {product_code}: {e}")
    
    if empty_nav_count == 0 and nav_mismatch_count == 0:
        print("所有净值校验通过")
    
    # ========== 第二部分：金额校验 ==========
    print("\n" + "=" * 80)
    print("【2】金额校验")
    print("=" * 80)
    print("买入: 份额×净值=金额, 金额+手续费=实际支出")
    print("卖出: 份额×净值=到账金额+手续费, 金额=到账金额\n")
    
    for i, txn in enumerate(transactions):
        action = txn.get('action', '').strip().upper()
        
        # 分红没有金额，跳过
        if action == 'DIVIDEND':
            continue
        
        product_code = txn.get('product_code', '').strip()
        txn_date = txn.get('date', '').strip()
        
        shares = safe_decimal(txn.get('shares', 0))
        nav = safe_decimal(txn.get('nav', 0))
        amount = safe_decimal(txn.get('amount', 0))
        fee = safe_decimal(txn.get('fee', 0))
        
        if shares == 0 or nav == 0:
            continue
        
        # 计算：份额 × 净值
        calculated = shares * nav
        
        # 根据买入/卖出区分校验逻辑
        if action == 'BUY':
            # 买入：份额 × 净值 = 金额
            expected = amount
            label = "金额"
        elif action == 'SELL':
            # 卖出：份额 × 净值 = 到账金额 + 手续费
            expected = amount + fee
            label = "到账金额+手续费"
        else:
            continue
        
        # 计算差异
        diff = abs(calculated - expected)
        
        # 允许一定误差（由于四舍五入，允许 0.5 元误差）
        tolerance = Decimal('0.5')
        
        if diff > tolerance:
            amount_mismatch_count += 1
            diff_pct = (diff / expected * 100) if expected > 0 else Decimal('0')
            
            print(f"[金额不匹配] {txn_date} {product_code} {action}")
            print(f"             份额={shares}, 净值={nav}, 手续费={fee}")
            print(f"             份额×净值={calculated:.2f}, {label}={expected:.2f}")
            print(f"             差异: {diff:.2f} ({diff_pct:.2f}%)")
            
            amount_mismatch_list.append({
                'date': txn_date,
                'product_code': product_code,
                'action': action,
                'shares': shares,
                'nav': nav,
                'calculated': calculated,
                'expected': expected,
                'diff': diff,
            })
    
    if amount_mismatch_count == 0:
        print("所有金额校验通过")
    
    # ========== 汇总 ==========
    print("\n" + "=" * 80)
    print("校验汇总")
    print("=" * 80)
    print(f"【净值校验】")
    print(f"  - 空净值: {empty_nav_count} 条")
    print(f"  - 净值不匹配: {nav_mismatch_count} 条")
    print(f"  - nav文件缺失: {nav_missing_count} 条")
    print(f"【金额校验】")
    print(f"  - 金额不匹配: {amount_mismatch_count} 条")
    
    # 保存修复
    if fix_mode and nav_fixed_count > 0:
        with open(transactions_path, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for txn in transactions:
                writer.writerow(txn)
        print(f"\n[OK] 已修复净值 {nav_fixed_count} 条，更新 transactions.csv")
    elif fix_mode:
        print(f"\n无需修复净值")
    else:
        if empty_nav_count + nav_mismatch_count > 0:
            print(f"\n提示: 使用 --fix 参数可自动修复净值问题")
    
    print("\n注: 金额不匹配可能是正常的（四舍五入、份额确认差异等），请人工核对")


def main():
    fix_mode = '--fix' in sys.argv
    
    if fix_mode:
        print("模式: 检查并修复\n")
    else:
        print("模式: 仅检查（不修改文件）\n")
    
    validate_transactions(fix_mode)


if __name__ == "__main__":
    main()
