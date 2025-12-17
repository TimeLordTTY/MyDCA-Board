"""测试智能去重与重建功能"""
import sys
import csv
import shutil
from pathlib import Path
from datetime import datetime, timedelta
from decimal import Decimal
import io

# 设置 stdout 为 UTF-8 编码（解决 Windows 控制台 Unicode 问题）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 添加src到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root / "src"))

# 导入需要测试的模块
from snapshot import create_daily_snapshot, rebuild_snapshots_from_date, read_all_snapshots

def create_test_snapshot(path, data):
    """创建测试快照文件"""
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ['fetch_date', 'product_code', 'product_name', 'category', 'nav_date', 'nav', 'shares', 'value', 'pnl', 'cost', 'unrealized_pnl', 'return_rate', 'fetched_at']
    
    with open(path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)

def test_skip_same_value():
    """测试1: value 相同时跳过"""
    print("\n" + "="*60)
    print("测试1: value 相同时跳过（重复数据）")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        today = datetime.now().strftime('%Y-%m-%d')
        
        # 初始数据
        initial_data = [
            {'fetch_date': today, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': today, 'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': f'{today} 10:00:00'}
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: fetch_date={today}, value=100.00")
        
        # 模拟第二次运行（同样的数据）
        holdings_map = {'TEST001': Decimal('100')}
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'nav_date': today,
                'nav': Decimal('1.00'),  # 净值相同
                'total_nav': Decimal('1.00'),
                'income': Decimal('0'),
                'weekly_rate': Decimal('0')
            }
        }
        
        count = create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=snapshot_path)
        
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 第二次运行结果:")
        print(f"  - 返回值: {count}（应为0，跳过）")
        print(f"  - 快照总数: {len(snapshots)}（应为1）")
        
        assert count == 0, f"预期跳过（返回0），实际返回{count}"
        assert len(snapshots) == 1, f"预期1条记录，实际{len(snapshots)}条"
        
        print("\n✓✓✓ 测试1通过: value相同时正确跳过")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试1失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_update_on_value_change():
    """测试2: value 变化时覆盖更新（份额变化）"""
    print("\n" + "="*60)
    print("测试2: value 变化时覆盖更新（份额变化）")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        today = datetime.now().strftime('%Y-%m-%d')
        
        # 初始数据（份额100）
        initial_data = [
            {'fetch_date': today, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': today, 'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': f'{today} 10:00:00'}
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: shares=100, value=100.00")
        
        # 模拟份额更新后的运行（份额变为110）
        holdings_map = {'TEST001': Decimal('110')}  # 份额变了
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'nav_date': today,  # 净值日期相同
                'nav': Decimal('1.00'),  # 净值相同
                'total_nav': Decimal('1.00'),
                'income': Decimal('0'),
                'weekly_rate': Decimal('0')
            }
        }
        
        count = create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=snapshot_path)
        
        snapshots = read_all_snapshots(snapshot_path)
        snap = snapshots[0]
        
        print(f"\n>>> 份额更新后运行结果:")
        print(f"  - 返回值: {count}（应为1，覆盖更新）")
        print(f"  - 快照总数: {len(snapshots)}（应为1）")
        print(f"  - 更新后 shares: {snap['shares']}（应为110）")
        print(f"  - 更新后 value: {snap['value']}（应为110.00）")
        
        assert count == 1, f"预期覆盖更新（返回1），实际返回{count}"
        assert len(snapshots) == 1, f"预期1条记录，实际{len(snapshots)}条"
        assert snap['shares'] == '110', f"预期shares=110，实际{snap['shares']}"
        assert float(snap['value']) == 110.00, f"预期value=110.00，实际{snap['value']}"
        
        print("\n✓✓✓ 测试2通过: 份额变化时正确覆盖更新")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试2失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_new_snapshot_date():
    """测试3: 不同 snapshot_date 时新增记录"""
    print("\n" + "="*60)
    print("测试3: 不同 snapshot_date 时新增记录")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        yesterday = (datetime.now() - timedelta(days=1)).strftime('%Y-%m-%d')
        today = datetime.now().strftime('%Y-%m-%d')
        
        # 初始数据（昨天的净值）
        initial_data = [
            {'fetch_date': yesterday, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': yesterday, 'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': f'{yesterday} 22:00:00'}
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: fetch_date={yesterday}, value=100.00")
        
        # 模拟新净值的运行
        holdings_map = {'TEST001': Decimal('100')}
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'nav_date': today,  # 新净值日期
                'nav': Decimal('1.05'),  # 净值上涨
                'total_nav': Decimal('1.05'),
                'income': Decimal('0'),
                'weekly_rate': Decimal('0')
            }
        }
        
        count = create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=snapshot_path)
        
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 新净值运行结果:")
        print(f"  - 返回值: {count}（应为1，新增）")
        print(f"  - 快照总数: {len(snapshots)}（应为2）")
        
        assert count == 1, f"预期新增（返回1），实际返回{count}"
        assert len(snapshots) == 2, f"预期2条记录，实际{len(snapshots)}条"
        
        # 验证两条记录都存在
        dates = [s['fetch_date'] for s in snapshots]
        assert yesterday in dates, f"应保留{yesterday}的记录"
        assert today in dates, f"应新增{today}的记录"
        
        print(f"  ✓ 保留了 {yesterday} 的记录")
        print(f"  ✓ 新增了 {today} 的记录")
        
        print("\n✓✓✓ 测试3通过: 不同净值日期正确新增")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试3失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_rebuild_mode():
    """测试4: 重建模式"""
    print("\n" + "="*60)
    print("测试4: 重建模式")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        # 创建多天的数据
        initial_data = [
            {'fetch_date': '2025-12-14', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': '2025-12-14', 'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': '2025-12-14 10:00:00'},
            {'fetch_date': '2025-12-15', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': '2025-12-15', 'nav': '1.10', 'shares': '100', 'value': '110.00', 'pnl': '10.00',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': '2025-12-15 10:00:00'},
            {'fetch_date': '2025-12-16', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': '2025-12-16', 'nav': '1.05', 'shares': '100', 'value': '105.00', 'pnl': '-5.00',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': '2025-12-16 10:00:00'},
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: 3天快照 (12-14, 12-15, 12-16)")
        
        # 执行重建：从12-16开始重建
        kept, deleted = rebuild_snapshots_from_date(snapshot_path, '2025-12-16')
        
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 重建结果:")
        print(f"  - 保留: {kept}条")
        print(f"  - 删除: {deleted}条")
        print(f"  - 剩余: {len(snapshots)}条")
        
        assert kept == 2, f"预期保留2条，实际{kept}条"
        assert deleted == 1, f"预期删除1条，实际{deleted}条"
        assert len(snapshots) == 2, f"预期剩余2条，实际{len(snapshots)}条"
        
        dates = [s['fetch_date'] for s in snapshots]
        assert '2025-12-14' in dates, "应保留12-14"
        assert '2025-12-15' in dates, "应保留12-15"
        assert '2025-12-16' not in dates, "应删除12-16"
        
        print(f"  ✓ 正确保留: 12-14, 12-15")
        print(f"  ✓ 正确删除: 12-16")
        
        print("\n✓✓✓ 测试4通过: 重建模式正确工作")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试4失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_fix_wrong_shares():
    """测试5: 修正写错的份额"""
    print("\n" + "="*60)
    print("测试5: 修正写错的份额（配置修正场景）")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        today = datetime.now().strftime('%Y-%m-%d')
        
        # 初始数据（份额写错了，写成1000）
        initial_data = [
            {'fetch_date': today, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'category': 'fund', 'nav_date': today, 'nav': '1.00', 'shares': '1000', 'value': '1000.00', 'pnl': '0',
             'cost': '0', 'unrealized_pnl': '0', 'return_rate': '0%', 'fetched_at': f'{today} 10:00:00'}
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 错误数据: shares=1000 (写错了), value=1000.00")
        
        # 修正后的运行（正确份额是100）
        holdings_map = {'TEST001': Decimal('100')}  # 修正为正确份额
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'nav_date': today,
                'nav': Decimal('1.00'),
                'total_nav': Decimal('1.00'),
                'income': Decimal('0'),
                'weekly_rate': Decimal('0')
            }
        }
        
        count = create_daily_snapshot(nav_records, holdings_map, products_map, snapshot_path=snapshot_path)
        
        snapshots = read_all_snapshots(snapshot_path)
        snap = snapshots[0]
        
        print(f"\n>>> 修正后运行结果:")
        print(f"  - 返回值: {count}（应为1，覆盖更新）")
        print(f"  - 修正后 shares: {snap['shares']}（应为100）")
        print(f"  - 修正后 value: {snap['value']}（应为100.00）")
        
        assert count == 1, f"预期覆盖更新（返回1），实际返回{count}"
        assert snap['shares'] == '100', f"预期shares=100，实际{snap['shares']}"
        assert float(snap['value']) == 100.00, f"预期value=100.00，实际{snap['value']}"
        
        print("\n✓✓✓ 测试5通过: 错误份额已正确修正")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试5失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def main():
    print("\n" + "="*60)
    print("     智能去重与重建功能测试")
    print("="*60)
    
    results = []
    results.append(("value相同时跳过", test_skip_same_value()))
    results.append(("value变化时覆盖", test_update_on_value_change()))
    results.append(("不同snapshot_date新增", test_new_snapshot_date()))
    results.append(("重建模式", test_rebuild_mode()))
    results.append(("修正错误份额", test_fix_wrong_shares()))
    
    # 汇总
    print("\n" + "="*60)
    print("测试汇总")
    print("="*60)
    for name, passed in results:
        status = "✓ 通过" if passed else "✗ 失败"
        print(f"  {status}  {name}")
    
    all_passed = all(r[1] for r in results)
    if all_passed:
        print("\n" + "="*60)
        print(f"🎉 所有测试通过 ({len(results)}/{len(results)})")
        print("智能去重功能验证成功！")
        print("="*60)
        return True
    else:
        failed = [name for name, passed in results if not passed]
        print(f"\n❌ 部分测试失败: {', '.join(failed)}")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
