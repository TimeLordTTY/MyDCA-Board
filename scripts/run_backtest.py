# -*- coding: utf-8 -*-
"""
=============================================================================
                        基金定投回测 - MyDCA-Board
=============================================================================

用法：
    python scripts/run_backtest.py                          # 使用默认配置
    python scripts/run_backtest.py --product 163406         # 指定产品
    python scripts/run_backtest.py --list                   # 列出可用产品
    python scripts/run_backtest.py --help                   # 查看帮助

功能：
    - 读取 MyDCA-Board 的 nav 数据进行回测
    - 支持多种策略（纯定投、止盈循环等）
    - 输出回测结果到 data/backtest_results/
"""

import sys
import os
import argparse
from pathlib import Path
from datetime import datetime

# 添加 src 目录到 Python 路径
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "src"))

from backtest.engine import DataFeed, Portfolio, Backtester
from backtest.engine.backtester import write_results_to_csv, write_summary_to_csv
from backtest.utils.nav_loader import load_nav_from_product, get_available_products
from backtest.strategies import STRATEGY_REGISTRY


def list_products():
    """列出所有可用于回测的产品"""
    products = get_available_products(PROJECT_ROOT)
    
    if not products:
        print("未找到可用的产品净值数据")
        print(f"请确保 {PROJECT_ROOT / 'data' / 'nav'} 目录下有净值文件")
        return
    
    print("\n" + "=" * 70)
    print("                    可用于回测的产品列表")
    print("=" * 70)
    print(f"\n{'产品代码':<15} {'产品名称'}")
    print("-" * 70)
    
    for p in products:
        print(f"{p['product_code']:<15} {p['product_name']}")
    
    print("\n" + "=" * 70)
    print(f"共 {len(products)} 个产品可用")


def list_strategies():
    """列出所有可用的策略"""
    strategies = STRATEGY_REGISTRY.list_strategies()
    
    print("\n" + "=" * 70)
    print("                    可用的回测策略")
    print("=" * 70)
    print(f"\n{'策略名称':<20} {'可用版本'}")
    print("-" * 70)
    
    for name, versions in strategies.items():
        versions_str = ', '.join(v for v in versions if v != 'default')
        print(f"{name:<20} {versions_str}")
    
    print("\n" + "=" * 70)


def run_backtest(
    product_code: str,
    strategy_name: str = "pure_sip",
    strategy_version: str = None,
    initial_invest: float = 1000.0,
    periodic_invest: float = 500.0,
    buy_fee_rate: float = 0.0012,
    sell_fee_rate: float = 0.0,
    start_date: str = None,
    end_date: str = None,
    invest_rule: str = "month_change",
    output_dir: str = None,
):
    """
    执行回测
    
    Args:
        product_code: 产品代码
        strategy_name: 策略名称
        strategy_version: 策略版本
        initial_invest: 初始投入金额
        periodic_invest: 定期投入金额
        buy_fee_rate: 买入费率
        sell_fee_rate: 卖出费率
        start_date: 回测起始日期
        end_date: 回测结束日期
        invest_rule: 定投规则（month_change/week_change）
        output_dir: 输出目录
    """
    print("=" * 70)
    print("                    基金定投策略回测")
    print("=" * 70)
    
    # 1. 加载净值数据
    print(f"\n📊 产品代码: {product_code}")
    
    try:
        bars = load_nav_from_product(product_code, PROJECT_ROOT)
        print(f"   数据条数: {len(bars)} 条")
        print(f"   时间范围: {bars[0].date.strftime('%Y-%m-%d')} ~ {bars[-1].date.strftime('%Y-%m-%d')}")
    except FileNotFoundError as e:
        print(f"\n❌ 错误: {e}")
        print("\n💡 提示: 运行 python scripts/run_backtest.py --list 查看可用产品")
        return
    except Exception as e:
        print(f"❌ 数据加载失败: {e}")
        return
    
    # 2. 创建数据源和组合
    data_feed = DataFeed(bars)
    portfolio = Portfolio(
        cash=0.0,
        buy_fee_rate=buy_fee_rate,
        sell_fee_rate=sell_fee_rate
    )
    
    # 3. 创建策略
    print(f"\n📈 策略类型: {strategy_name}")
    if strategy_version:
        print(f"   策略版本: {strategy_version}")
    
    try:
        strategy_class = STRATEGY_REGISTRY.get(strategy_name, strategy_version)
        strategy = strategy_class({})
        
        print(f"   策略名称: {strategy.get_name()}")
        print(f"   策略标识: {strategy.get_strategy_key()}")
    except ValueError as e:
        print(f"❌ 策略加载失败: {e}")
        list_strategies()
        return
    
    print(f"\n💰 初始投入: {initial_invest:,.2f} 元")
    print(f"💰 定期投入: {periodic_invest:,.2f} 元")
    print(f"📊 买入费率: {buy_fee_rate:.4%}")
    print(f"📊 卖出费率: {sell_fee_rate:.4%}")
    print(f"📅 定投规则: {'每月' if invest_rule == 'month_change' else '每周'}第一个交易日")
    
    if start_date or end_date:
        print(f"\n📅 自定义回测区间:")
        if start_date:
            print(f"   起始日期: {start_date}")
        if end_date:
            print(f"   结束日期: {end_date}")
    
    # 4. 创建回测引擎
    backtester = Backtester(
        data_feed=data_feed,
        portfolio=portfolio,
        strategy=strategy,
        initial_invest=initial_invest,
        periodic_invest=periodic_invest,
        invest_day_rule=invest_rule,
        start_date=start_date,
        end_date=end_date,
        fund_code=product_code,
    )
    
    # 5. 执行回测
    print(f"\n⏳ 正在执行回测...")
    results = backtester.run()
    
    # 6. 打印摘要
    backtester.print_summary()
    
    # 7. 保存结果
    if output_dir is None:
        output_dir = PROJECT_ROOT / "data" / "backtest_results"
    else:
        output_dir = Path(output_dir)
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 生成文件名前缀
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    prefix = f"backtest_{product_code}_{strategy_name}_{timestamp}"
    
    # 保存明细
    details_file = output_dir / f"{prefix}_details.csv"
    write_results_to_csv(results, str(details_file))
    
    # 保存摘要
    summary_file = output_dir / f"{prefix}_summary.csv"
    write_summary_to_csv(backtester.get_summary(), str(summary_file))
    
    print(f"\n📁 已生成文件:")
    print(f"   - {details_file}")
    print(f"   - {summary_file}")
    
    print("\n" + "=" * 70)
    print("✅ 回测完成")
    print("=" * 70)
    
    return backtester.get_summary()


def main():
    parser = argparse.ArgumentParser(
        description='MyDCA-Board 基金定投回测工具',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
示例:
  # 列出可用产品
  python scripts/run_backtest.py --list
  
  # 列出可用策略
  python scripts/run_backtest.py --strategies
  
  # 使用默认配置回测
  python scripts/run_backtest.py --product 163406
  
  # 自定义配置
  python scripts/run_backtest.py --product 163406 \\
    --initial 2000 \\
    --periodic 500 \\
    --start 2020-01-01 \\
    --end 2025-12-01
        '''
    )
    
    parser.add_argument('--list', action='store_true', help='列出可用于回测的产品')
    parser.add_argument('--strategies', action='store_true', help='列出可用的策略')
    parser.add_argument('--product', '-p', type=str, default='163406', help='产品代码（默认: 163406）')
    parser.add_argument('--strategy', '-s', type=str, default='pure_sip', help='策略名称（默认: pure_sip）')
    parser.add_argument('--version', '-v', type=str, default=None, help='策略版本')
    parser.add_argument('--initial', type=float, default=1000.0, help='初始投入金额（默认: 1000）')
    parser.add_argument('--periodic', type=float, default=500.0, help='定期投入金额（默认: 500）')
    parser.add_argument('--buy-fee', type=float, default=0.0012, help='买入费率（默认: 0.12%%）')
    parser.add_argument('--sell-fee', type=float, default=0.0, help='卖出费率（默认: 0）')
    parser.add_argument('--start', type=str, default=None, help='回测起始日期（YYYY-MM-DD）')
    parser.add_argument('--end', type=str, default=None, help='回测结束日期（YYYY-MM-DD）')
    parser.add_argument('--rule', type=str, default='month_change', 
                        choices=['month_change', 'week_change'],
                        help='定投规则（默认: month_change）')
    parser.add_argument('--output', '-o', type=str, default=None, help='输出目录')
    
    args = parser.parse_args()
    
    if args.list:
        list_products()
        return
    
    if args.strategies:
        list_strategies()
        return
    
    run_backtest(
        product_code=args.product,
        strategy_name=args.strategy,
        strategy_version=args.version,
        initial_invest=args.initial,
        periodic_invest=args.periodic,
        buy_fee_rate=args.buy_fee,
        sell_fee_rate=args.sell_fee,
        start_date=args.start,
        end_date=args.end,
        invest_rule=args.rule,
        output_dir=args.output,
    )


if __name__ == "__main__":
    main()

