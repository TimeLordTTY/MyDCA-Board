"""测试资产汇总功能"""
import sys
from pathlib import Path

# 添加src到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root / "src"))

def test_portfolio_summary():
    """测试投资组合汇总生成"""
    from portfolio_summary import generate_portfolio_summary
    from config_loader import get_project_root
    
    print("=" * 60)
    print("测试资产汇总功能")
    print("=" * 60)
    
    root = get_project_root()
    snapshot_path = root / "data" / "snapshots" / "daily.csv"
    output_dir = root / "data" / "snapshots"
    
    print(f"\n输入文件: {snapshot_path}")
    print(f"输出目录: {output_dir}")
    
    # 生成汇总
    print("\n>>> 生成汇总文件...")
    nav_count, fetch_count = generate_portfolio_summary(snapshot_path, output_dir)
    
    print(f"\n✓ 生成成功:")
    print(f"  - 按净值日期汇总: {nav_count} 个日期")
    print(f"  - 按采集日期汇总: {fetch_count} 个日期")
    
    # 显示生成的文件
    nav_date_file = output_dir / "portfolio_by_nav_date.csv"
    fetch_date_file = output_dir / "portfolio_by_fetch_date.csv"
    
    print(f"\n生成的文件:")
    print(f"  1. {nav_date_file}")
    print(f"  2. {fetch_date_file}")
    
    # 显示示例内容
    print("\n" + "=" * 60)
    print("按净值日期汇总 (前3行)")
    print("=" * 60)
    if nav_date_file.exists():
        with open(nav_date_file, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f):
                if i < 3:
                    print(line.strip())
    
    print("\n" + "=" * 60)
    print("按采集日期汇总 (前3行)")
    print("=" * 60)
    if fetch_date_file.exists():
        with open(fetch_date_file, 'r', encoding='utf-8') as f:
            for i, line in enumerate(f):
                if i < 3:
                    print(line.strip())
    
    print("\n" + "=" * 60)
    print("✓ 测试完成")
    print("=" * 60)

if __name__ == "__main__":
    test_portfolio_summary()

