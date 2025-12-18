"""最小化自测脚本 - 验证系统可控性"""
import sys
import json
import shutil
from pathlib import Path
import io

# 设置 stdout 为 UTF-8 编码（解决 Windows 控制台 Unicode 问题）
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 添加src到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root / "src"))

def test_duplicate_run():
    """测试1: 重复运行不应重复写入同一天数据"""
    print("\n" + "="*60)
    print("测试1: 重复运行幂等性测试")
    print("="*60)
    
    from nav_collector import collect_and_store
    
    # 第一次运行
    print("\n>>> 第一次运行...")
    collect_and_store()
    
    # 读取结果
    data_dir = project_root / "data" / "nav"
    first_run_files = {f.name: f.stat().st_size for f in data_dir.glob("*.csv")}
    
    # 第二次运行
    print("\n>>> 第二次运行...")
    collect_and_store()
    
    # 读取结果
    second_run_files = {f.name: f.stat().st_size for f in data_dir.glob("*.csv")}
    
    # 验证文件大小不变（说明没有重复写入）
    print("\n>>> 验证结果:")
    all_passed = True
    for filename in first_run_files:
        if filename in second_run_files:
            if first_run_files[filename] == second_run_files[filename]:
                print(f"  ✓ {filename}: 大小不变 (幂等性OK)")
            else:
                print(f"  ✗ {filename}: 大小变化 {first_run_files[filename]} -> {second_run_files[filename]} (有重复写入)")
                all_passed = False
    
    if all_passed:
        print("\n✓✓✓ 测试1通过: 重复运行不会重复写入")
        return True
    else:
        print("\n✗✗✗ 测试1失败: 检测到重复写入")
        return False

def test_invalid_holdings():
    """测试2: holdings中不存在的产品ID应报错退出"""
    print("\n" + "="*60)
    print("测试2: 配置校验测试（故意写错）")
    print("="*60)
    
    holdings_path = project_root / "config" / "holdings.json"
    backup_path = project_root / "config" / "holdings.json.bak"
    
    # 备份原配置
    shutil.copy(holdings_path, backup_path)
    
    try:
        # 修改配置，添加一个不存在的产品ID
        with open(holdings_path, 'r', encoding='utf-8') as f:
            holdings = json.load(f)
        
        # 添加一个不存在的产品
        holdings.append({
            "product_code": "INVALID_PRODUCT_999",
            "amount": 100
        })
        
        with open(holdings_path, 'w', encoding='utf-8') as f:
            json.dump(holdings, f, indent=4, ensure_ascii=False)
        
        print("\n>>> 已在holdings.json中添加无效产品ID: INVALID_PRODUCT_999")
        print(">>> 尝试运行采集程序...")
        
        # 尝试运行（应该报错退出）
        from nav_collector import collect_and_store
        
        try:
            collect_and_store()
            print("\n✗✗✗ 测试2失败: 程序没有检测到配置错误")
            return False
        except SystemExit as e:
            if e.code == 1:
                print("\n✓✓✓ 测试2通过: 程序正确检测到配置错误并退出")
                return True
            else:
                print(f"\n✗✗✗ 测试2失败: 退出码不正确 ({e.code})")
                return False
    
    finally:
        # 恢复原配置
        shutil.copy(backup_path, holdings_path)
        backup_path.unlink()
        print("\n>>> 已恢复原配置")

def test_missing_product_fields():
    """测试3: products中缺少必需字段应报错"""
    print("\n" + "="*60)
    print("测试3: 产品配置完整性测试")
    print("="*60)
    
    products_path = project_root / "config" / "products.json"
    backup_path = project_root / "config" / "products.json.bak"
    
    # 备份原配置
    shutil.copy(products_path, backup_path)
    
    try:
        # 修改配置，删除一个必需字段
        with open(products_path, 'r', encoding='utf-8') as f:
            products = json.load(f)
        
        # 删除第一个产品的source字段
        if products and 'source' in products[0]:
            del products[0]['source']
            
            with open(products_path, 'w', encoding='utf-8') as f:
                json.dump(products, f, indent=4, ensure_ascii=False)
            
            print(f"\n>>> 已删除产品 {products[0]['product_code']} 的 source 字段")
            print(">>> 尝试运行采集程序...")
            
            # 重新导入以清除缓存
            import importlib
            import nav_collector
            importlib.reload(nav_collector)
            
            try:
                nav_collector.collect_and_store()
                print("\n✗✗✗ 测试3失败: 程序没有检测到缺失字段")
                return False
            except SystemExit as e:
                if e.code == 1:
                    print("\n✓✓✓ 测试3通过: 程序正确检测到缺失字段并退出")
                    return True
                else:
                    print(f"\n✗✗✗ 测试3失败: 退出码不正确 ({e.code})")
                    return False
    
    finally:
        # 恢复原配置
        shutil.copy(backup_path, products_path)
        backup_path.unlink()
        print("\n>>> 已恢复原配置")

def test_decimal_dirty_data():
    """测试4: Decimal脏数据测试"""
    print("\n" + "="*60)
    print("测试4: Decimal脏数据测试")
    print("="*60)
    
    import tempfile
    import csv
    from pathlib import Path
    
    sys.path.insert(0, str(project_root / "src"))
    from portfolio_summary import generate_portfolio_summary
    
    # 创建临时目录
    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir_path = Path(tmpdir)
        
        # 构造包含脏数据的 mock daily.csv
        daily_csv = tmpdir_path / "daily.csv"
        mock_data = [
            # 正常数据
            {'snapshot_date': '2025-12-16', 'product_code': 'A001', 'product_name': '产品A', 
             'nav': '1.1000', 'shares': '100', 'value': '110.00', 'pnl': '5.00', 
             'fetched_at': '2025-12-16 10:00:00'},
            # value为空字符串
            {'snapshot_date': '2025-12-16', 'product_code': 'B002', 'product_name': '产品B', 
             'nav': '2.2000', 'shares': '50', 'value': '', 'pnl': '3.00', 
             'fetched_at': '2025-12-16 10:00:00'},
            # pnl为"-"
            {'snapshot_date': '2025-12-16', 'product_code': 'C003', 'product_name': '产品C', 
             'nav': '3.0000', 'shares': '30', 'value': '90.00', 'pnl': '-', 
             'fetched_at': '2025-12-16 10:00:00'},
            # value包含逗号
            {'snapshot_date': '2025-12-16', 'product_code': 'D004', 'product_name': '产品D', 
             'nav': '4.0000', 'shares': '100', 'value': '12,345.67', 'pnl': '100.50', 
             'fetched_at': '2025-12-16 10:00:00'},
        ]
        
        with open(daily_csv, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=list(mock_data[0].keys()))
            writer.writeheader()
            writer.writerows(mock_data)
        
        print(f"\n>>> 创建包含脏数据的mock: {len(mock_data)}条记录")
        print("  - value为空字符串（应按0处理）")
        print("  - pnl为'-'（应按0处理）")
        print("  - value包含逗号'12,345.67'（应解析为12345.67）")
        
        # 生成汇总（应该不崩溃）
        try:
            nav_count, fetch_count = generate_portfolio_summary(daily_csv, tmpdir_path)
            print(f"\n>>> ✓ 汇总生成成功（未崩溃）: {nav_count}个净值日期, {fetch_count}个采集日期")
        except Exception as e:
            print(f"\n>>> ✗ 汇总生成失败（不应该崩溃）: {e}")
            import traceback
            traceback.print_exc()
            return False
        
        # 验证输出文件生成
        fetch_file = tmpdir_path / "portfolio_by_fetch_date.csv"
        assert fetch_file.exists(), "portfolio_by_fetch_date.csv 应该生成"
        
        # 验证数据正确性
        with open(fetch_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        
        assert len(rows) == 1, "应该有1条记录"
        row = rows[0]
        
        # 正常值110.00 + 空值按0 + 正常值90.00 + 带逗号12345.67 = 12545.67
        expected_value = 110.00 + 0 + 90.00 + 12345.67
        actual_value = float(row['total_value'])
        
        print(f"\n>>> 验证汇总结果:")
        print(f"  - 期望total_value: {expected_value}")
        print(f"  - 实际total_value: {actual_value}")
        
        assert abs(actual_value - expected_value) < 0.01, f"total_value计算错误"
        
        print("\n✓✓✓ 测试4通过: Decimal脏数据处理正确")
        return True

def test_fetched_at_formats():
    """测试5: fetched_at多格式测试"""
    print("\n" + "="*60)
    print("测试5: fetched_at多格式测试")
    print("="*60)
    
    import tempfile
    import csv
    from pathlib import Path
    
    sys.path.insert(0, str(project_root / "src"))
    from portfolio_summary import generate_portfolio_summary
    
    # 创建临时目录
    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir_path = Path(tmpdir)
        
        # 构造包含不同时间格式的 mock daily.csv
        daily_csv = tmpdir_path / "daily.csv"
        mock_data = [
            # 格式1: "YYYY-MM-DD HH:MM:SS"
            {'snapshot_date': '2025-12-16', 'product_code': 'A001', 'product_name': '产品A', 
             'nav': '1.1000', 'shares': '100', 'value': '110.00', 'pnl': '0', 
             'fetched_at': '2025-12-16 22:54:52'},
            # 格式2: "YYYY-MM-DDTHH:MM:SS"
            {'snapshot_date': '2025-12-16', 'product_code': 'B002', 'product_name': '产品B', 
             'nav': '2.2000', 'shares': '50', 'value': '110.00', 'pnl': '0', 
             'fetched_at': '2025-12-16T22:54:52'},
            # 格式3: "YYYY-MM-DDTHH:MM:SS.sss"
            {'snapshot_date': '2025-12-16', 'product_code': 'C003', 'product_name': '产品C', 
             'nav': '3.0000', 'shares': '30', 'value': '90.00', 'pnl': '0', 
             'fetched_at': '2025-12-16T22:54:52.123'},
            # 格式4: "YYYY-MM-DDTHH:MM:SS+08:00"
            {'snapshot_date': '2025-12-16', 'product_code': 'D004', 'product_name': '产品D', 
             'nav': '4.0000', 'shares': '25', 'value': '100.00', 'pnl': '0', 
             'fetched_at': '2025-12-16T22:54:52+08:00'},
            # 格式5: "YYYY-MM-DDTHH:MM:SSZ"
            {'snapshot_date': '2025-12-16', 'product_code': 'E005', 'product_name': '产品E', 
             'nav': '5.0000', 'shares': '20', 'value': '100.00', 'pnl': '0', 
             'fetched_at': '2025-12-16T22:54:52Z'},
        ]
        
        with open(daily_csv, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=list(mock_data[0].keys()))
            writer.writeheader()
            writer.writerows(mock_data)
        
        print(f"\n>>> 创建包含多种时间格式的mock: {len(mock_data)}条记录")
        print("  - 格式1: 2025-12-16 22:54:52")
        print("  - 格式2: 2025-12-16T22:54:52")
        print("  - 格式3: 2025-12-16T22:54:52.123")
        print("  - 格式4: 2025-12-16T22:54:52+08:00")
        print("  - 格式5: 2025-12-16T22:54:52Z")
        
        # 生成汇总
        nav_count, fetch_count = generate_portfolio_summary(daily_csv, tmpdir_path)
        print(f"\n>>> 汇总生成: {nav_count}个净值日期, {fetch_count}个采集日期")
        
        # 验证所有记录都聚合到同一个fetch_date
        fetch_file = tmpdir_path / "portfolio_by_fetch_date.csv"
        with open(fetch_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        
        print(f"\n>>> 验证聚合结果:")
        assert len(rows) == 1, f"所有记录应该聚合到1个日期，实际有{len(rows)}个"
        
        row = rows[0]
        assert row['fetch_date'] == '2025-12-16', f"fetch_date应为2025-12-16，实际为{row['fetch_date']}"
        assert row['product_count'] == '5', f"应有5个产品，实际有{row['product_count']}个"
        
        print(f"  ✓ 所有5种时间格式都正确聚合到: {row['fetch_date']}")
        print(f"  ✓ 产品数量: {row['product_count']}")
        print(f"  ✓ 总市值: {row['total_value']}")
        
        print("\n✓✓✓ 测试5通过: fetched_at多格式支持正确")
        return True

def test_portfolio_summary():
    """测试6: 资产汇总功能（原测试4）"""
    print("\n" + "="*60)
    print("测试6: 资产汇总功能测试")
    print("="*60)
    
    import tempfile
    import csv
    from datetime import datetime, timedelta
    from pathlib import Path
    
    sys.path.insert(0, str(project_root / "src"))
    from portfolio_summary import generate_portfolio_summary
    
    # 创建临时目录
    with tempfile.TemporaryDirectory() as tmpdir:
        tmpdir_path = Path(tmpdir)
        
        # 构造 mock daily.csv
        # 场景：2025-12-16采集，有些产品是12-15的净值（滞后1天），有些是12-16的净值
        daily_csv = tmpdir_path / "daily.csv"
        mock_data = [
            # 产品A：12-16净值，12-16采集
            {'snapshot_date': '2025-12-16', 'product_code': 'A001', 'product_name': '产品A', 
             'nav': '1.1000', 'shares': '100', 'value': '110.00', 'pnl': '5.00', 
             'fetched_at': '2025-12-16 10:00:00'},
            # 产品B：12-15净值，12-16采集（滞后1天）
            {'snapshot_date': '2025-12-15', 'product_code': 'B002', 'product_name': '产品B', 
             'nav': '2.2000', 'shares': '50', 'value': '110.00', 'pnl': '3.00', 
             'fetched_at': '2025-12-16 10:00:00'},
            # 产品C：12-14净值，12-16采集（滞后2天，最大滞后）
            {'snapshot_date': '2025-12-14', 'product_code': 'C003', 'product_name': '产品C', 
             'nav': '3.0000', 'shares': '30', 'value': '90.00', 'pnl': '2.00', 
             'fetched_at': '2025-12-16 10:00:00'},
            # 产品A：12-17净值，12-17采集（第二天）
            {'snapshot_date': '2025-12-17', 'product_code': 'A001', 'product_name': '产品A', 
             'nav': '1.1500', 'shares': '100', 'value': '115.00', 'pnl': '5.00', 
             'fetched_at': '2025-12-17 10:00:00'},
            # 产品B：12-17净值，12-17采集
            {'snapshot_date': '2025-12-17', 'product_code': 'B002', 'product_name': '产品B', 
             'nav': '2.3000', 'shares': '50', 'value': '115.00', 'pnl': '5.00', 
             'fetched_at': '2025-12-17 10:00:00'},
        ]
        
        with open(daily_csv, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=list(mock_data[0].keys()))
            writer.writeheader()
            writer.writerows(mock_data)
        
        print(f"\n>>> 创建mock数据: {len(mock_data)}条记录")
        
        # 生成汇总
        nav_count, fetch_count = generate_portfolio_summary(daily_csv, tmpdir_path)
        print(f">>> 生成汇总: {nav_count}个净值日期, {fetch_count}个采集日期")
        
        # 验证 portfolio_by_fetch_date.csv
        fetch_file = tmpdir_path / "portfolio_by_fetch_date.csv"
        with open(fetch_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            fetch_rows = list(reader)
        
        print("\n>>> 验证按采集日期汇总（主口径：同一采集日的所有产品都汇总在一起）:")
        
        # 第一天：2025-12-16
        row1 = fetch_rows[0]
        assert row1['fetch_date'] == '2025-12-16', "fetch_date错误"
        assert row1['product_count'] == '3', f"product_count应为3(去重)，实际{row1['product_count']}"
        assert row1['stale_products'] == '2', f"stale_products应为2，实际{row1['stale_products']}"
        assert row1['max_lag_days'] == '2', f"max_lag_days应为2，实际{row1['max_lag_days']}"
        assert float(row1['total_value']) == 310.00, f"total_value应为310.00，实际{row1['total_value']}"
        assert float(row1['total_pnl_vs_prev_fetch']) == 0.00, "第一天的total_pnl_vs_prev_fetch应为0"
        print(f"  ✓ 2025-12-16: 3个产品（12-16、12-15、12-14净值日）全部汇总到同一行")
        print(f"    - total_value={row1['total_value']}（完整资产，避免被拆散）")
        print(f"    - stale_products={row1['stale_products']}（2个产品净值滞后）")
        print(f"    - max_lag_days={row1['max_lag_days']}（最大滞后2天）")
        
        # 第二天：2025-12-17
        row2 = fetch_rows[1]
        assert row2['fetch_date'] == '2025-12-17', "fetch_date错误"
        assert row2['product_count'] == '2', f"product_count应为2，实际{row2['product_count']}"
        assert row2['stale_products'] == '0', f"stale_products应为0，实际{row2['stale_products']}"
        assert row2['max_lag_days'] == '0', f"max_lag_days应为0，实际{row2['max_lag_days']}"
        assert float(row2['total_value']) == 230.00, f"total_value应为230.00，实际{row2['total_value']}"
        # 230 - 310 = -80
        assert float(row2['total_pnl_vs_prev_fetch']) == -80.00, f"total_pnl_vs_prev_fetch应为-80.00，实际{row2['total_pnl_vs_prev_fetch']}"
        print(f"  ✓ 2025-12-17: value={row2['total_value']}, daily_change={row2['total_pnl_vs_prev_fetch']}")
        
        # 验证 portfolio_by_nav_date.csv
        nav_file = tmpdir_path / "portfolio_by_nav_date.csv"
        with open(nav_file, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            nav_rows = list(reader)
        
        print("\n>>> 验证按净值日期汇总（辅助口径：按交易日拆分）:")
        print(f"  ✓ 共{len(nav_rows)}个不同净值日期（同一采集日的产品被拆散）")
        
        # 验证产品去重
        for row in nav_rows:
            print(f"    - {row['snapshot_date']}: {row['product_count']}个产品, value={row['total_value']}")
        
        print("\n✓✓✓ 测试4通过: 资产汇总功能正确")
        return True

def test_transactions_cost():
    """测试7: 交易流水与成本计算"""
    print("\n" + "="*60)
    print("测试7: 交易流水与成本计算")
    print("="*60)
    
    import csv
    import tempfile
    import shutil
    from decimal import Decimal
    from holdings_calculator import calc_position_from_transactions, load_transactions
    from snapshot import create_daily_snapshot, read_all_snapshots
    
    # 创建临时目录
    tmpdir = tempfile.mkdtemp()
    tmpdir_path = Path(tmpdir)
    
    try:
        # 创建交易流水mock数据
        transactions_path = tmpdir_path / "transactions.csv"
        transactions_data = [
            # 买入100份，花费1000元，手续费5元
            {'date': '2025-12-10', 'product_code': 'TEST001', 'action': 'BUY', 'amount': '1000', 'shares': '100', 'fee': '5', 'note': '首次买入'},
            # 再买入50份，花费600元，手续费3元
            {'date': '2025-12-12', 'product_code': 'TEST001', 'action': 'BUY', 'amount': '600', 'shares': '50', 'fee': '3', 'note': '加仓'},
            # 卖出30份
            {'date': '2025-12-14', 'product_code': 'TEST001', 'action': 'SELL', 'amount': '400', 'shares': '30', 'fee': '2', 'note': '减仓'},
        ]
        
        with open(transactions_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=['date', 'product_code', 'action', 'amount', 'shares', 'fee', 'note'])
            writer.writeheader()
            writer.writerows(transactions_data)
        
        print("\n>>> 创建交易流水mock数据:")
        print("  - 2025-12-10: 买入100份，花费1000+5=1005元")
        print("  - 2025-12-12: 买入50份，花费600+3=603元")
        print("  - 2025-12-14: 卖出30份")
        
        # 测试 calc_position_from_transactions
        shares, cost = calc_position_from_transactions('TEST001', '2025-12-15', transactions_path)
        
        # 预期结果：
        # 买入后：shares = 100 + 50 = 150, cost = 1005 + 603 = 1608
        # 卖出30份后：shares = 150 - 30 = 120
        # 成本按比例减少：cost = 1608 * (120/150) = 1608 * 0.8 = 1286.4
        expected_shares = Decimal('120')
        expected_cost = Decimal('1608') * Decimal('120') / Decimal('150')  # 1286.4
        
        print(f"\n>>> 验证持仓计算:")
        print(f"  - 计算得到: shares={shares}, cost={cost:.2f}")
        print(f"  - 预期: shares={expected_shares}, cost={expected_cost:.2f}")
        
        assert shares == expected_shares, f"份额计算错误: 预期{expected_shares}, 实际{shares}"
        assert abs(cost - expected_cost) < Decimal('0.01'), f"成本计算错误: 预期{expected_cost:.2f}, 实际{cost:.2f}"
        
        print(f"  ✓ 份额计算正确: {shares}")
        print(f"  ✓ 成本计算正确: {cost:.2f}")
        
        # 测试快照生成 - 创建带成本的快照
        snapshot_path = tmpdir_path / "daily.csv"
        
        # 模拟净值记录
        nav_records = {
            'TEST001': {
                'nav_date': '2025-12-15',
                'nav': Decimal('11.00'),  # 当前净值
                'total_nav': Decimal('11.00'),
                'income': Decimal('0'),
                'weekly_rate': Decimal('0')
            }
        }
        
        # holdings_map 作为回退（但本测试中不会用到，因为有交易流水）
        holdings_map = {'TEST001': Decimal('0')}
        products_map = {'TEST001': 'Test Product A'}
        
        # 临时设置 holdings_calculator 的默认路径
        import holdings_calculator
        original_load = holdings_calculator.load_transactions
        holdings_calculator.load_transactions = lambda path=None: load_transactions(transactions_path)
        
        # 生成快照
        count = create_daily_snapshot(nav_records, holdings_map, products_map, 
                                     snapshot_path=snapshot_path)
        
        # 恢复原始函数
        holdings_calculator.load_transactions = original_load
        
        print(f"\n>>> 验证快照生成:")
        print(f"  - 生成 {count} 条快照")
        
        # 读取快照验证
        snapshots = read_all_snapshots(snapshot_path)
        assert len(snapshots) == 1, f"预期1条快照，实际{len(snapshots)}条"
        
        snap = snapshots[0]
        snap_shares = Decimal(snap['shares'])
        snap_cost = Decimal(snap['cost'])
        snap_value = Decimal(snap['value'])
        snap_unrealized_pnl = Decimal(snap['unrealized_pnl'])
        
        # 验证 value = shares * nav = 120 * 11 = 1320
        expected_value = expected_shares * Decimal('11.00')
        # 验证 unrealized_pnl = value - cost = 1320 - 1286.4 = 33.6
        expected_unrealized_pnl = expected_value - expected_cost
        
        print(f"  - 快照内容: shares={snap_shares}, cost={snap_cost:.2f}, value={snap_value:.2f}, unrealized_pnl={snap_unrealized_pnl:.2f}")
        print(f"  - 预期: value={expected_value:.2f}, unrealized_pnl={expected_unrealized_pnl:.2f}")
        
        assert abs(snap_value - expected_value) < Decimal('0.01'), f"value计算错误: 预期{expected_value:.2f}, 实际{snap_value:.2f}"
        assert abs(snap_unrealized_pnl - expected_unrealized_pnl) < Decimal('0.01'), f"unrealized_pnl计算错误: 预期{expected_unrealized_pnl:.2f}, 实际{snap_unrealized_pnl:.2f}"
        
        print(f"  ✓ value正确: {snap_value:.2f}")
        print(f"  ✓ unrealized_pnl正确: {snap_unrealized_pnl:.2f} (浮盈)")
        
        print("\n✓✓✓ 测试7通过: 交易流水与成本计算正确")
        return True
        
    except Exception as e:
        print(f"\n✗✗✗ 测试7失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        # 清理临时目录
        shutil.rmtree(tmpdir, ignore_errors=True)

def main():
    """运行所有测试"""
    print("\n" + "█"*60)
    print("█" + " "*18 + "财富中枢自测程序" + " "*18 + "█")
    print("█"*60)
    
    results = []
    
    # 测试1: 幂等性
    try:
        results.append(("重复运行幂等性", test_duplicate_run()))
    except Exception as e:
        print(f"\n✗✗✗ 测试1异常: {e}")
        results.append(("重复运行幂等性", False))
    
    # 测试2: 配置校验
    try:
        results.append(("配置错误检测", test_invalid_holdings()))
    except Exception as e:
        print(f"\n✗✗✗ 测试2异常: {e}")
        results.append(("配置错误检测", False))
    
    # 测试3: 字段完整性
    try:
        results.append(("字段完整性检查", test_missing_product_fields()))
    except Exception as e:
        print(f"\n✗✗✗ 测试3异常: {e}")
        results.append(("字段完整性检查", False))
    
    # 测试4: Decimal脏数据
    try:
        results.append(("Decimal脏数据处理", test_decimal_dirty_data()))
    except Exception as e:
        print(f"\n✗✗✗ 测试4异常: {e}")
        import traceback
        traceback.print_exc()
        results.append(("Decimal脏数据处理", False))
    
    # 测试5: fetched_at多格式
    try:
        results.append(("fetched_at多格式支持", test_fetched_at_formats()))
    except Exception as e:
        print(f"\n✗✗✗ 测试5异常: {e}")
        import traceback
        traceback.print_exc()
        results.append(("fetched_at多格式支持", False))
    
    # 测试6: 资产汇总
    try:
        results.append(("资产汇总功能", test_portfolio_summary()))
    except Exception as e:
        print(f"\n✗✗✗ 测试6异常: {e}")
        import traceback
        traceback.print_exc()
        results.append(("资产汇总功能", False))
    
    # 测试7: 交易流水与成本计算
    try:
        results.append(("交易流水与成本计算", test_transactions_cost()))
    except Exception as e:
        print(f"\n✗✗✗ 测试7异常: {e}")
        import traceback
        traceback.print_exc()
        results.append(("交易流水与成本计算", False))
    
    # 汇总
    print("\n" + "="*60)
    print("测试汇总")
    print("="*60)
    for name, passed in results:
        status = "✓ 通过" if passed else "✗ 失败"
        print(f"  {status}  {name}")
    
    passed_count = sum(1 for _, p in results if p)
    total_count = len(results)
    
    print("\n" + "="*60)
    if passed_count == total_count:
        print(f"🎉 所有测试通过 ({passed_count}/{total_count})")
        print("系统可控性验证成功！")
    else:
        print(f"⚠️  部分测试失败 ({passed_count}/{total_count})")
        print("请检查失败的测试项")
    print("="*60 + "\n")
    
    return passed_count == total_count

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)

