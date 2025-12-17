"""测试强制覆盖与重建功能 - 简化版"""
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
    fieldnames = ['snapshot_date', 'product_code', 'product_name', 'nav', 'shares', 'value', 'pnl', 'fetched_at']
    
    with open(path, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(data)

def test_default_mode_idempotent():
    """测试1: 默认模式的幂等性（多次运行不重复写入）"""
    print("\n" + "="*60)
    print("测试1: 默认模式幂等性")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        # 清理测试目录
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        # 创建初始数据：12-16的快照
        today = datetime.now().strftime('%Y-%m-%d')
        initial_data = [
            {'snapshot_date': today, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'fetched_at': f'{today} 10:00:00'}
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: 1条快照")
        
        # 模拟第二次运行（同样的数据）
        holdings_map = {'TEST001': Decimal('100')}
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'ISS_DATE': today,
                'NAV': Decimal('1.00'),
                'TOT_NAV': Decimal('1.00'),
                'INCOME': Decimal('0'),
                'WEEK_CLIENTRATE': Decimal('0')
            }
        }
        
        # 默认模式运行（直接传入snapshot_path）
        count = create_daily_snapshot(nav_records, holdings_map, products_map, 
                                     force_overwrite=False, snapshot_path=snapshot_path)
        
        # 验证
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 第二次运行结果:")
        print(f"  - 返回值: {count}")
        print(f"  - 快照总数: {len(snapshots)}")
        if len(snapshots) > 0:
            for i, snap in enumerate(snapshots):
                print(f"  - 快照{i+1}: {snap['snapshot_date']}, {snap['product_code']}, nav={snap['nav']}, fetched_at={snap['fetched_at']}")
        
        assert count == 0, f"预期跳过（返回0），实际返回{count}"
        assert len(snapshots) == 1, f"预期仍为1条记录，实际{len(snapshots)}条"
        
        print(f"\n  ✓ 第二次运行返回: {count}（跳过）")
        print(f"  ✓ 快照总数: {len(snapshots)}（未增加）")
        print("\n✓✓✓ 测试1通过: 默认模式幂等性正确")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试1失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_force_mode_overwrite():
    """测试2: 强制覆盖模式（同一采集日的快照会被覆盖）"""
    print("\n" + "="*60)
    print("测试2: 强制覆盖模式")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        # 清理测试目录
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        # 创建初始数据（模拟上午10点的采集）
        today = datetime.now()
        today_str = today.strftime('%Y-%m-%d')
        yesterday_str = (today - timedelta(days=1)).strftime('%Y-%m-%d')
        
        initial_data = [
            {'snapshot_date': yesterday_str, 'product_code': 'TEST001', 'product_name': 'Test Product',
             'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'fetched_at': f'{today_str} 10:00:00'}  # 今天上午10点采集
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: 今天10:00采集, snapshot_date={yesterday_str}, nav=1.00")
        
        # 模拟同一天下午的采集（净值更新了）
        holdings_map = {'TEST001': Decimal('100')}
        products_map = {'TEST001': 'Test Product'}
        nav_records = {
            'TEST001': {
                'ISS_DATE': today_str,  # 净值日期更新为今天
                'NAV': Decimal('1.05'),  # 净值上涨
                'TOT_NAV': Decimal('1.05'),
                'INCOME': Decimal('0'),
                'WEEK_CLIENTRATE': Decimal('0')
            }
        }
        
        # 强制覆盖模式运行（直接传入snapshot_path）
        print(f">>> 执行强制覆盖模式...")
        count = create_daily_snapshot(nav_records, holdings_map, products_map, 
                                     force_overwrite=True, snapshot_path=snapshot_path)
        
        # 验证
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 覆盖后数据:")
        print(f"  - 返回值: {count}")
        print(f"  - 快照总数: {len(snapshots)}")
        print(f"  - 快照内容: {snapshots[0]}")
        
        # 关键验证
        assert len(snapshots) == 1, f"预期1条记录（覆盖），实际{len(snapshots)}条"
        assert count == 1, f"预期返回1（更新），实际返回{count}"
        
        snap = snapshots[0]
        assert snap['snapshot_date'] == today_str, f"预期snapshot_date={today_str}，实际{snap['snapshot_date']}"
        assert snap['nav'] == '1.05', f"预期nav=1.05（已覆盖），实际{snap['nav']}"
        assert snap['value'] == '105.00', f"预期value=105.00，实际{snap['value']}"
        assert today_str in snap['fetched_at'], f"预期fetched_at包含{today_str}，实际{snap['fetched_at']}"
        
        print(f"\n  ✓ snapshot_date更新: {yesterday_str} -> {today_str}")
        print(f"  ✓ nav更新: 1.00 -> 1.05")
        print(f"  ✓ value更新: 100.00 -> 105.00")
        print("\n✓✓✓ 测试2通过: 强制覆盖模式正确工作")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试2失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_rebuild_mode():
    """测试3: 重建模式（删除指定日期及之后的快照）"""
    print("\n" + "="*60)
    print("测试3: 重建模式")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        # 清理测试目录
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        # 创建多天的数据
        initial_data = [
            {'snapshot_date': '2025-12-14', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'fetched_at': '2025-12-14 10:00:00'},
            {'snapshot_date': '2025-12-15', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'nav': '1.10', 'shares': '100', 'value': '110.00', 'pnl': '10.00',
             'fetched_at': '2025-12-15 10:00:00'},
            {'snapshot_date': '2025-12-16', 'product_code': 'TEST001', 'product_name': 'Test Product',
             'nav': '1.05', 'shares': '100', 'value': '105.00', 'pnl': '-5.00',
             'fetched_at': '2025-12-16 10:00:00'},
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: 3天快照 (12-14, 12-15, 12-16)")
        
        # 执行重建：从12-16开始重建
        kept, deleted = rebuild_snapshots_from_date(snapshot_path, '2025-12-16')
        
        # 验证
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 重建结果:")
        print(f"  - 保留: {kept}条")
        print(f"  - 删除: {deleted}条")
        print(f"  - 剩余: {len(snapshots)}条")
        
        assert kept == 2, f"预期保留2条，实际{kept}条"
        assert deleted == 1, f"预期删除1条，实际{deleted}条"
        assert len(snapshots) == 2, f"预期剩余2条，实际{len(snapshots)}条"
        
        # 验证保留的是正确的数据
        dates = [s['snapshot_date'] for s in snapshots]
        assert '2025-12-14' in dates, "应保留12-14"
        assert '2025-12-15' in dates, "应保留12-15"
        assert '2025-12-16' not in dates, "应删除12-16"
        
        print(f"  ✓ 正确保留: 12-14, 12-15")
        print(f"  ✓ 正确删除: 12-16")
        print("\n✓✓✓ 测试3通过: 重建模式正确工作")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试3失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def test_force_mode_multiple_products():
    """测试4: 强制覆盖模式 - 多产品场景"""
    print("\n" + "="*60)
    print("测试4: 强制覆盖模式 - 多产品")
    print("="*60)
    
    test_dir = project_root / "test_data"
    snapshot_path = test_dir / "snapshots" / "daily.csv"
    
    try:
        # 清理测试目录
        if test_dir.exists():
            shutil.rmtree(test_dir)
        
        today = datetime.now().strftime('%Y-%m-%d')
        yesterday = (datetime.now() - timedelta(days=1)).strftime('%Y-%m-%d')
        
        # 初始数据：今天上午采集了2个产品
        initial_data = [
            {'snapshot_date': yesterday, 'product_code': 'TEST001', 'product_name': 'Product A',
             'nav': '1.00', 'shares': '100', 'value': '100.00', 'pnl': '0',
             'fetched_at': f'{today} 10:00:00'},
            {'snapshot_date': yesterday, 'product_code': 'TEST002', 'product_name': 'Product B',
             'nav': '2.00', 'shares': '50', 'value': '100.00', 'pnl': '0',
             'fetched_at': f'{today} 10:00:00'},
        ]
        create_test_snapshot(snapshot_path, initial_data)
        print(f">>> 初始数据: 2个产品（上午10:00）")
        
        # 下午：只更新其中一个产品
        holdings_map = {'TEST001': Decimal('100'), 'TEST002': Decimal('50')}
        products_map = {'TEST001': 'Product A', 'TEST002': 'Product B'}
        nav_records = {
            'TEST001': {  # 只更新产品A
                'ISS_DATE': today,
                'NAV': Decimal('1.10'),
                'TOT_NAV': Decimal('1.10'),
                'INCOME': Decimal('0'),
                'WEEK_CLIENTRATE': Decimal('0')
            }
        }
        
        # 强制覆盖模式：只传入一个产品（直接传入snapshot_path）
        count = create_daily_snapshot(nav_records, holdings_map, products_map, 
                                     force_overwrite=True, snapshot_path=snapshot_path)
        
        # 验证
        snapshots = read_all_snapshots(snapshot_path)
        
        print(f"\n>>> 结果:")
        print(f"  - 返回值: {count}")
        print(f"  - 快照总数: {len(snapshots)}")
        
        # 验证：应该还有2条记录
        assert len(snapshots) == 2, f"预期2条记录，实际{len(snapshots)}条"
        
        # 找到两个产品的快照
        snap_a = next(s for s in snapshots if s['product_code'] == 'TEST001')
        snap_b = next(s for s in snapshots if s['product_code'] == 'TEST002')
        
        # TEST001应该被更新
        assert snap_a['nav'] == '1.10', f"TEST001 nav应为1.10，实际{snap_a['nav']}"
        assert snap_a['snapshot_date'] == today, f"TEST001 snapshot_date应为{today}"
        
        # TEST002应该保持不变
        assert snap_b['nav'] == '2.00', f"TEST002 nav应保持2.00，实际{snap_b['nav']}"
        assert snap_b['snapshot_date'] == yesterday, f"TEST002 snapshot_date应保持{yesterday}"
        
        print(f"  ✓ TEST001更新: nav 1.00 -> 1.10")
        print(f"  ✓ TEST002保持: nav 2.00（未变）")
        print("\n✓✓✓ 测试4通过: 多产品覆盖正确")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试4失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if test_dir.exists():
            shutil.rmtree(test_dir)

def main():
    print("\n" + "="*60)
    print("     强制覆盖与重建功能测试")
    print("="*60)
    
    results = []
    results.append(("默认模式幂等性", test_default_mode_idempotent()))
    results.append(("强制覆盖模式", test_force_mode_overwrite()))
    results.append(("重建模式", test_rebuild_mode()))
    results.append(("多产品覆盖", test_force_mode_multiple_products()))
    
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
        print("🎉 所有测试通过 (4/4)")
        print("强制覆盖与重建功能验证成功！")
        print("="*60)
        return True
    else:
        failed = [name for name, passed in results if not passed]
        print(f"\n❌ 部分测试失败: {', '.join(failed)}")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
