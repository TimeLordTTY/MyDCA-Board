"""最小化自测脚本 - 验证系统可控性"""
import sys
import json
import shutil
from pathlib import Path

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
            "products_id": "INVALID_PRODUCT_999",
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
            
            print(f"\n>>> 已删除产品 {products[0]['id']} 的 source 字段")
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

