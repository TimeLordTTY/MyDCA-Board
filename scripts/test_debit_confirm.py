#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
自测脚本：验证扣款(buy_debit)/份额确认(buy_confirm)分离场景

模拟时序：
1. 12/18 早：新 NAV 出来，统计一次（初始状态，1000份，nav=1.5000）
2. 12/18 晚：发生 buy_debit（扣款100元），统计一次
   - 预期: cash=100, shares=1000（不变）, pnl_day=0
3. 12/19 早：新 NAV 出来（1.5000 → 1.5200），统计一次
   - 预期: pnl_day = 1000 * (1.52 - 1.50) = 20（只由净值变化贡献）
4. 12/19 上午：buy_confirm 到账（65份），统计一次
   - 预期: shares=1065, cash=0, pnl_day=20（不因确认而跳变）

验证要点：
- ✅ 扣款后 cash 增加、shares 不变
- ✅ 份额到账后 shares 增加、cash 归零
- ✅ pnl_day 只由净值变化贡献，不受扣款/确认影响
- ✅ 同日多次统计只保留一条（覆盖）
"""
import sys
import os
import csv
import tempfile
import shutil
from pathlib import Path
from datetime import datetime
from decimal import Decimal
import io

# 设置 stdout 编码
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# 添加 src 目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))


def setup_test_data(test_dir: Path):
    """创建测试数据"""
    
    # 创建目录结构
    (test_dir / "data" / "snapshots").mkdir(parents=True, exist_ok=True)
    (test_dir / "config").mkdir(parents=True, exist_ok=True)
    
    # 创建 products.json
    products_json = test_dir / "config" / "products.json"
    with open(products_json, 'w', encoding='utf-8') as f:
        f.write('''[
    {"product_code": "TEST001", "product_name": "测试基金A", "source": "fund", "type": "fund", "category": "fund"}
]''')
    
    # 创建 holdings.json（基础持仓 1000 份）
    holdings_json = test_dir / "config" / "holdings.json"
    with open(holdings_json, 'w', encoding='utf-8') as f:
        f.write('''[
    {"product_code": "TEST001", "product_name": "测试基金A", "amount": 1000}
]''')
    
    return test_dir


def create_snapshot_with_date(nav_records, holdings_map, products_map, snapshot_path, 
                               category_map, simulated_date, transactions_path, holdings_path):
    """创建指定日期的快照"""
    from holdings_calculator import HoldingsCalculator
    from snapshot import get_prev_snapshot, read_all_snapshots, FIELDNAMES, CHINESE_HEADERS
    import uuid
    
    # 构造模拟的 fetched_at
    fetched_at = f"{simulated_date} 12:00:00.000"
    fetch_date = simulated_date
    
    # 创建持仓计算器
    calc = HoldingsCalculator(transactions_path, holdings_path)
    all_holdings = calc.get_holdings_as_of(fetch_date)
    all_cash_in_transit = calc.get_cash_in_transit_as_of(fetch_date)
    all_principal = calc.get_principal_total_as_of(fetch_date)
    
    # 读取所有现有快照
    all_snapshots = read_all_snapshots(snapshot_path)
    existing_map = {}
    for row in all_snapshots:
        row_fetch_date = row.get('fetch_date', row['fetched_at'][:10])
        key = (row_fetch_date, row['product_code'])
        existing_map[key] = row
    
    # 处理每个产品
    for product_code, nav_record in nav_records.items():
        product_name = products_map.get(product_code, '')
        nav_date = nav_record['nav_date']
        key = (fetch_date, product_code)
        
        # 获取份额和成本
        if product_code in all_holdings:
            shares = all_holdings[product_code]["shares"]
            cost = all_holdings[product_code]["cost"]
        else:
            shares = Decimal(str(holdings_map.get(product_code, 0)))
            cost = Decimal('0')
        
        nav = Decimal(str(nav_record['nav']))
        value = shares * nav
        
        # 获取在途资金
        cash = all_cash_in_transit.get(product_code, Decimal('0'))
        
        # 获取累计投入本金
        principal_total = all_principal.get(product_code, Decimal('0'))
        
        # 计算总资产
        total_value = value + cash
        
        # 计算总盈亏
        total_pnl = total_value - principal_total if principal_total > 0 else Decimal('0')
        
        # 计算真实收益率
        if principal_total > 0:
            real_return = (total_pnl / principal_total * 100)
        else:
            real_return = Decimal('0')
        
        # 计算 unrealized_pnl
        unrealized_pnl = value - cost if cost > 0 else Decimal('0')
        
        # 计算 return_rate
        if cost > 0:
            return_rate = (unrealized_pnl / cost * 100)
        else:
            return_rate = Decimal('0')
        
        # 计算 pnl_day（核心：只由净值变化贡献）
        prev_snapshot = get_prev_snapshot(snapshot_path, product_code, fetch_date)
        if prev_snapshot is not None:
            prev_shares = prev_snapshot['shares']
            prev_nav = prev_snapshot['nav']
            pnl_day = prev_shares * (nav - prev_nav)
        else:
            pnl_day = Decimal('0')
        
        # 构建新记录
        new_row = {
            'fetch_date': fetch_date,
            'product_code': product_code,
            'product_name': product_name,
            'category': category_map.get(product_code, 'fund'),
            'nav_date': nav_date,
            'nav': str(nav),
            'shares': f"{shares:.2f}",
            'value': f"{value:.2f}",
            'pnl_day': f"{pnl_day:.2f}",
            'cost': f"{cost:.2f}",
            'unrealized_pnl': f"{unrealized_pnl:.2f}",
            'return_rate': f"{return_rate:.2f}%",
            'cash': f"{cash:.2f}",
            'total_value': f"{total_value:.2f}",
            'principal_total': f"{principal_total:.2f}",
            'total_pnl': f"{total_pnl:.2f}",
            'real_return': f"{real_return:.2f}%",
            'fetched_at': fetched_at
        }
        
        existing_map[key] = new_row
    
    # 原子写入文件
    tmp_path = snapshot_path.parent / f"daily.csv.tmp.{uuid.uuid4().hex[:8]}"
    with open(tmp_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES, extrasaction='ignore')
        writer.writeheader()
        f.write(','.join([CHINESE_HEADERS.get(field, field) for field in FIELDNAMES]) + '\n')
        
        sorted_snapshots = sorted(existing_map.values(), key=lambda x: x['fetch_date'])
        for snapshot in sorted_snapshots:
            writer.writerow(snapshot)
    
    os.replace(str(tmp_path), str(snapshot_path))
    
    return new_row


def run_scenario(test_dir: Path):
    """运行完整场景"""
    
    snapshot_path = test_dir / "data" / "snapshots" / "daily.csv"
    transactions_path = test_dir / "data" / "transactions.csv"
    holdings_path = test_dir / "config" / "holdings.json"
    
    results = []
    
    # 初始化交易记录文件（10 列格式）
    with open(transactions_path, 'w', encoding='utf-8') as f:
        f.write("date,product_code,action,amount,shares,fee,nav,nav_date,order_id,note\n")
    
    # ============ 场景 1: 12/18 早 - 初始状态 ============
    print("\n" + "="*60)
    print("场景 1: 12/18 早 - 初始状态（nav=1.5000，基础持仓1000份）")
    print("="*60)
    
    nav_records = {'TEST001': {'nav': '1.5000', 'nav_date': '2025-12-17'}}
    row = create_snapshot_with_date(
        nav_records,
        {'TEST001': 1000},
        {'TEST001': '测试基金A'},
        snapshot_path,
        {'TEST001': 'fund'},
        '2025-12-18',
        transactions_path,
        holdings_path
    )
    results.append({
        'scenario': '1. 12/18早 初始',
        'shares': row['shares'],
        'cash': row['cash'],
        'value': row['value'],
        'total_value': row['total_value'],
        'pnl_day': row['pnl_day'],
        'principal_total': row['principal_total']
    })
    print(f"  shares={row['shares']}, cash={row['cash']}, value={row['value']}, pnl_day={row['pnl_day']}")
    
    # ============ 场景 2: 12/18 晚 - 发生扣款 ============
    print("\n" + "="*60)
    print("场景 2: 12/18 晚 - 发生 buy_debit（扣款 100 元，order_id=ORD001）")
    print("="*60)
    
    # 添加扣款记录（10 列格式）
    with open(transactions_path, 'a', encoding='utf-8') as f:
        f.write("2025-12-18,TEST001,buy_debit,100,,0,,,ORD001,扣款测试\n")
    
    row = create_snapshot_with_date(
        nav_records,
        {'TEST001': 1000},
        {'TEST001': '测试基金A'},
        snapshot_path,
        {'TEST001': 'fund'},
        '2025-12-18',
        transactions_path,
        holdings_path
    )
    results.append({
        'scenario': '2. 12/18晚 扣款',
        'shares': row['shares'],
        'cash': row['cash'],
        'value': row['value'],
        'total_value': row['total_value'],
        'pnl_day': row['pnl_day'],
        'principal_total': row['principal_total']
    })
    print(f"  shares={row['shares']}, cash={row['cash']}, value={row['value']}, total_value={row['total_value']}, pnl_day={row['pnl_day']}")
    print(f"  principal_total={row['principal_total']}")
    print(f"  ✓ 预期: shares=1000（不变）, cash=100（增加）, pnl_day=0（不跳变）")
    
    # ============ 场景 3: 12/19 早 - 新 NAV（价格变化）============
    print("\n" + "="*60)
    print("场景 3: 12/19 早 - 新 NAV（1.5000 → 1.5200）")
    print("="*60)
    
    nav_records = {'TEST001': {'nav': '1.5200', 'nav_date': '2025-12-18'}}
    row = create_snapshot_with_date(
        nav_records,
        {'TEST001': 1000},
        {'TEST001': '测试基金A'},
        snapshot_path,
        {'TEST001': 'fund'},
        '2025-12-19',
        transactions_path,
        holdings_path
    )
    results.append({
        'scenario': '3. 12/19早 新NAV',
        'shares': row['shares'],
        'cash': row['cash'],
        'value': row['value'],
        'total_value': row['total_value'],
        'pnl_day': row['pnl_day'],
        'principal_total': row['principal_total']
    })
    # 预期 pnl_day = 1000 * (1.52 - 1.50) = 20（按上一日已确认份额计算）
    print(f"  shares={row['shares']}, cash={row['cash']}, value={row['value']}, pnl_day={row['pnl_day']}")
    print(f"  ✓ 预期: pnl_day = 1000 * (1.52 - 1.50) = 20.00")
    
    # ============ 场景 4: 12/19 上午 - 份额确认 ============
    print("\n" + "="*60)
    print("场景 4: 12/19 上午 - buy_confirm（确认 65 份，order_id=ORD001）")
    print("="*60)
    
    # 添加确认记录（10 列格式）
    with open(transactions_path, 'a', encoding='utf-8') as f:
        f.write("2025-12-19,TEST001,buy_confirm,,65,,1.5385,2025-12-18,ORD001,确认测试\n")
    
    row = create_snapshot_with_date(
        nav_records,
        {'TEST001': 1000},
        {'TEST001': '测试基金A'},
        snapshot_path,
        {'TEST001': 'fund'},
        '2025-12-19',
        transactions_path,
        holdings_path
    )
    results.append({
        'scenario': '4. 12/19上午 确认',
        'shares': row['shares'],
        'cash': row['cash'],
        'value': row['value'],
        'total_value': row['total_value'],
        'pnl_day': row['pnl_day'],
        'principal_total': row['principal_total'],
        'cost': row['cost']
    })
    print(f"  shares={row['shares']}, cash={row['cash']}, value={row['value']}, total_value={row['total_value']}, pnl_day={row['pnl_day']}")
    print(f"  cost={row['cost']}, principal_total={row['principal_total']}")
    print(f"  ✓ 预期: shares=1065（增加65）, cash=0（归零）, pnl_day=20（不跳变）")
    
    # ============ 验证同日覆盖 ============
    print("\n" + "="*60)
    print("验证: 同日多次统计只保留一条")
    print("="*60)
    
    snapshot_count = count_snapshots_by_date(snapshot_path, 'TEST001')
    print(f"  各日期快照数量: {snapshot_count}")
    for date, count in snapshot_count.items():
        if count > 1:
            print(f"  ✗ {date} 有 {count} 条记录（应该只有1条）")
        else:
            print(f"  ✓ {date} 只有 1 条记录")
    
    # ============ 汇总结果 ============
    print("\n" + "="*60)
    print("验证结果汇总")
    print("="*60)
    header = f"{'场景':<20} {'shares':>10} {'cash':>10} {'value':>12} {'total_value':>12} {'pnl_day':>10} {'principal':>12}"
    print(header)
    print("-" * 90)
    for r in results:
        print(f"{r['scenario']:<20} {r['shares']:>10} {r['cash']:>10} {r['value']:>12} {r['total_value']:>12} {r['pnl_day']:>10} {r['principal_total']:>12}")
    
    return results


def count_snapshots_by_date(snapshot_path, product_code):
    """统计每个日期的快照数量"""
    counts = {}
    if not Path(snapshot_path).exists():
        return counts
    
    with open(snapshot_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get('fetch_date', '').startswith('采集'):
                continue
            if row['product_code'] == product_code:
                date = row['fetch_date']
                counts[date] = counts.get(date, 0) + 1
    return counts


def run_assertions(results):
    """运行关键断言验证"""
    print("\n" + "="*60)
    print("关键断言验证")
    print("="*60)
    
    all_passed = True
    
    # 断言 1: 场景2 - 扣款后 cash 应该 = 100
    r2 = results[1]
    cash_2 = Decimal(r2['cash'])
    if abs(cash_2 - Decimal('100')) < Decimal('0.01'):
        print("✓ 断言1: 扣款后 cash = 100")
    else:
        print(f"✗ 断言1: 扣款后 cash 应该 = 100（实际: {cash_2}）")
        all_passed = False
    
    # 断言 2: 场景2 - 扣款后 shares 不变（仍为 1000）
    r1 = results[0]
    if r1['shares'] == r2['shares']:
        print("✓ 断言2: 扣款后 shares 不变")
    else:
        print(f"✗ 断言2: 扣款后 shares 应该不变 ({r1['shares']} vs {r2['shares']})")
        all_passed = False
    
    # 断言 3: 场景2 - 扣款后 principal_total = 100
    principal_2 = Decimal(r2['principal_total'])
    if abs(principal_2 - Decimal('100')) < Decimal('0.01'):
        print("✓ 断言3: 扣款后 principal_total = 100")
    else:
        print(f"✗ 断言3: 扣款后 principal_total 应该 = 100（实际: {principal_2}）")
        all_passed = False
    
    # 断言 4: 场景3 - pnl_day = 20
    r3 = results[2]
    pnl_3 = Decimal(r3['pnl_day'])
    if abs(pnl_3 - Decimal('20.00')) < Decimal('0.01'):
        print("✓ 断言4: 场景3 pnl_day = 20.00（只由净值变化贡献）")
    else:
        print(f"✗ 断言4: 场景3 pnl_day 应该 = 20.00（实际: {pnl_3}）")
        all_passed = False
    
    # 断言 5: 场景4 - 确认后 shares = 1065
    r4 = results[3]
    shares_4 = Decimal(r4['shares'])
    if abs(shares_4 - Decimal('1065')) < Decimal('0.01'):
        print("✓ 断言5: 确认后 shares = 1065（增加 65）")
    else:
        print(f"✗ 断言5: 确认后 shares 应该 = 1065（实际: {shares_4}）")
        all_passed = False
    
    # 断言 6: 场景4 - 确认后 cash = 0
    cash_4 = Decimal(r4['cash'])
    if cash_4 == 0:
        print("✓ 断言6: 确认后 cash = 0（归零）")
    else:
        print(f"✗ 断言6: 确认后 cash 应该 = 0（实际: {cash_4}）")
        all_passed = False
    
    # 断言 7: 场景4 - pnl_day 仍然 = 20（确认不影响）
    pnl_4 = Decimal(r4['pnl_day'])
    if abs(pnl_4 - Decimal('20.00')) < Decimal('0.01'):
        print("✓ 断言7: 确认后 pnl_day = 20.00（不因确认而跳变）")
    else:
        print(f"✗ 断言7: 确认后 pnl_day 应该 = 20.00（实际: {pnl_4}）")
        all_passed = False
    
    # 断言 8: 场景4 - cost 应该 = 100（净申购额）
    cost_4 = Decimal(r4['cost'])
    if abs(cost_4 - Decimal('100')) < Decimal('0.01'):
        print("✓ 断言8: 确认后 cost = 100（净申购额）")
    else:
        print(f"✗ 断言8: 确认后 cost 应该 = 100（实际: {cost_4}）")
        all_passed = False
    
    return all_passed


def main():
    """主函数"""
    print("="*60)
    print("扣款(buy_debit)/份额确认(buy_confirm)分离验证脚本")
    print("="*60)
    
    # 使用临时目录进行测试
    test_dir = Path(tempfile.mkdtemp(prefix="mydca_test_"))
    print(f"测试目录: {test_dir}")
    
    try:
        # 设置环境变量让 config_loader 使用测试目录
        os.environ['MYDCA_PROJECT_ROOT'] = str(test_dir)
        
        setup_test_data(test_dir)
        results = run_scenario(test_dir)
        all_passed = run_assertions(results)
        
        print("\n" + "="*60)
        if all_passed:
            print("🎉 所有验证通过！")
            print("系统正确实现了 buy_debit/buy_confirm 分离模式：")
            print("  - 扣款时 cash 增加、shares 不变")
            print("  - 确认时 shares 增加、cash 归零")
            print("  - pnl_day 只由净值变化贡献，不受扣款/确认影响")
        else:
            print("❌ 部分验证失败，请检查")
        print("="*60)
        
        return 0 if all_passed else 1
        
    finally:
        # 清理测试目录
        shutil.rmtree(test_dir, ignore_errors=True)
        if 'MYDCA_PROJECT_ROOT' in os.environ:
            del os.environ['MYDCA_PROJECT_ROOT']


if __name__ == "__main__":
    sys.exit(main())

