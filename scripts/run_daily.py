"""日常采集任务入口"""
import sys
import argparse
from pathlib import Path

# 将src目录添加到Python路径
src_path = Path(__file__).parent.parent / "src"
sys.path.insert(0, str(src_path))

from nav_collector import collect_and_store

def main():
    parser = argparse.ArgumentParser(
        description='财富中枢净值采集系统',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
使用示例:
  # 正常采集（智能去重：value变化则覆盖，否则跳过）
  python scripts/run_daily.py
  
  # 重建模式：从指定日期重建快照与 PnL 链
  python scripts/run_daily.py --rebuild-from 2025-12-01

智能去重策略（按 snapshot_date + product_code）：
  - 不存在 → 新增记录
  - 存在但 value 变化 → 覆盖更新（份额变化或配置修正）
  - 存在且 value 相同 → 跳过（重复数据）
        '''
    )
    
    parser.add_argument(
        '--rebuild-from',
        type=str,
        metavar='YYYY-MM-DD',
        help='从指定日期重建快照（会删除该日期及之后的快照，并重新生成 PnL 链）'
    )
    
    args = parser.parse_args()
    
    # 参数验证
    if args.rebuild_from:
        # 简单验证日期格式
        import re
        if not re.match(r'^\d{4}-\d{2}-\d{2}$', args.rebuild_from):
            print(f"错误: --rebuild-from 参数格式错误，应为 YYYY-MM-DD，实际为 {args.rebuild_from}")
            sys.exit(1)
    
    # 执行采集
    collect_and_store(rebuild_from=args.rebuild_from)

if __name__ == "__main__":
    main()
