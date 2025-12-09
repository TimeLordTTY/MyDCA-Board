"""
命令行入口

使用示例：
    python main.py --csv nav_163406.csv --fund 163406 --strategy sip
    python main.py --csv nav_163406.csv --fund 163406 --strategy tp_dip
"""

import argparse
import sys
import os

# 确保可以导入本地模块
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fund_backtest.engine import DataFeed, Portfolio, Backtester
from fund_backtest.engine.backtester import write_results_to_csv
from fund_backtest.utils.csv_loader import load_nav_series, create_sample_csv
from fund_backtest.strategies import SipStrategy, TpDipStrategy


# 策略注册表：策略名称 -> 策略类
STRATEGY_REGISTRY = {
    'sip': SipStrategy,
    'tp_dip': TpDipStrategy,
}


def main():
    """命令行主函数"""
    parser = argparse.ArgumentParser(
        description='基金定投策略回测引擎',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
示例：
  python main.py --csv nav_163406.csv --fund 163406 --strategy sip
  python main.py --csv nav_163406.csv --fund 163406 --strategy tp_dip
  python main.py --generate-sample  # 生成示例数据

可用策略：
  sip     - 普通定投策略（每月定额买入，不止盈不补仓）
  tp_dip  - 止盈补仓策略（多档止盈 + 多档逢低加仓）
'''
    )
    
    parser.add_argument(
        '--csv', 
        type=str,
        help='净值 CSV 文件路径'
    )
    
    parser.add_argument(
        '--fund', 
        type=str,
        default='unknown',
        help='基金代码（用于标记输出文件）'
    )
    
    parser.add_argument(
        '--strategy', 
        type=str,
        default='sip',
        choices=list(STRATEGY_REGISTRY.keys()),
        help='策略名称'
    )
    
    parser.add_argument(
        '--initial', 
        type=float,
        default=10000.0,
        help='初始一次性投入金额（默认 10000）'
    )
    
    parser.add_argument(
        '--periodic', 
        type=float,
        default=1000.0,
        help='每月定投金额（默认 1000）'
    )
    
    parser.add_argument(
        '--buy-fee', 
        type=float,
        default=0.0015,
        help='买入手续费率（默认 0.0015，即 0.15%%）'
    )
    
    parser.add_argument(
        '--sell-fee', 
        type=float,
        default=0.005,
        help='卖出手续费率（默认 0.005，即 0.5%%）'
    )
    
    parser.add_argument(
        '--date-col',
        type=str,
        default='date',
        help='CSV 中日期列的名称（默认 date）'
    )
    
    parser.add_argument(
        '--nav-col',
        type=str,
        default='nav',
        help='CSV 中净值列的名称（默认 nav）'
    )
    
    parser.add_argument(
        '--output', 
        type=str,
        help='输出 CSV 文件路径（默认自动生成）'
    )
    
    parser.add_argument(
        '--generate-sample',
        action='store_true',
        help='生成示例 CSV 数据文件'
    )
    
    args = parser.parse_args()
    
    # 生成示例数据
    if args.generate_sample:
        sample_path = 'sample_nav.csv'
        create_sample_csv(sample_path, days=365*3)  # 3年数据
        print(f"✅ 已生成示例数据文件: {sample_path}")
        print(f"   可以使用以下命令运行回测:")
        print(f"   python main.py --csv {sample_path} --strategy sip")
        return
    
    # 检查必需参数
    if not args.csv:
        parser.error("请指定 --csv 参数（净值数据文件路径）")
    
    if not os.path.exists(args.csv):
        print(f"❌ 错误：找不到文件 {args.csv}")
        print(f"   提示：可以运行 'python main.py --generate-sample' 生成示例数据")
        sys.exit(1)
    
    print("=" * 60)
    print("基金定投策略回测引擎")
    print("=" * 60)
    
    # 1. 加载数据
    print(f"\n📊 加载数据: {args.csv}")
    try:
        bars = load_nav_series(
            args.csv, 
            date_col=args.date_col,
            nav_col=args.nav_col
        )
        print(f"   共 {len(bars)} 条净值数据")
        print(f"   时间范围: {bars[0].date.strftime('%Y-%m-%d')} ~ {bars[-1].date.strftime('%Y-%m-%d')}")
    except Exception as e:
        print(f"❌ 数据加载失败: {e}")
        sys.exit(1)
    
    # 2. 创建数据源
    data_feed = DataFeed(bars)
    
    # 3. 创建组合
    portfolio = Portfolio(
        cash=0.0,
        buy_fee_rate=args.buy_fee,
        sell_fee_rate=args.sell_fee
    )
    
    # 4. 创建策略
    print(f"\n📈 策略: {args.strategy}")
    strategy_class = STRATEGY_REGISTRY[args.strategy]
    
    # 策略配置
    if args.strategy == 'sip':
        strategy_config = {
            'immediate_invest': True,
        }
    elif args.strategy == 'tp_dip':
        strategy_config = {
            'tp_levels': [
                {"threshold": 0.10, "sell_ratio": 0.25},
                {"threshold": 0.20, "sell_ratio": 0.25},
                {"threshold": 0.30, "sell_ratio": 0.50},
            ],
            'dip_levels': [
                {"drawdown": 0.05, "extra_amount": 500},
                {"drawdown": 0.10, "extra_amount": 1000},
                {"drawdown": 0.15, "extra_amount": 1500},
            ],
            'max_dip_buy_ratio_of_cash': 1.0,
            'tp_reference': 'cost',
            'auto_invest_inflow': True,  # 每月新增资金也会买入
        }
    else:
        strategy_config = {}
    
    strategy = strategy_class(strategy_config)
    
    # 5. 创建回测引擎
    backtester = Backtester(
        data_feed=data_feed,
        portfolio=portfolio,
        strategy=strategy,
        initial_invest=args.initial,
        periodic_invest=args.periodic,
        invest_day_rule="month_change"
    )
    
    # 6. 执行回测
    print(f"\n⏳ 执行回测...")
    print(f"   初始投入: {args.initial:.2f}")
    print(f"   每月定投: {args.periodic:.2f}")
    print(f"   买入费率: {args.buy_fee:.4%}")
    print(f"   卖出费率: {args.sell_fee:.4%}")
    
    results = backtester.run()
    
    # 7. 输出结果
    summary = backtester.get_summary()
    
    print("\n" + "=" * 60)
    print("📊 回测结果摘要")
    print("=" * 60)
    print(f"回测区间: {summary['start_date']} ~ {summary['end_date']} ({summary['days']} 天)")
    print(f"累计投入本金: {summary['total_cost']:.2f}")
    print(f"期末基金市值: {summary['final_fund_value']:.2f}")
    print(f"期末现金余额: {summary['final_cash']:.2f}")
    print(f"期末总资产: {summary['final_value']:.2f}")
    print(f"总收益率: {summary['total_return']:.2%}")
    print(f"年化收益率: {summary['annual_return']:.2%}")
    print(f"买入次数: {summary['buy_count']}")
    print(f"卖出次数: {summary['sell_count']}")
    print(f"总买入金额: {summary['total_buy']:.2f}")
    print(f"总卖出金额: {summary['total_sell']:.2f}")
    
    # 输出策略特定统计
    if 'tp_count' in summary:
        print(f"止盈次数: {summary['tp_count']}")
    if 'dip_count' in summary:
        print(f"补仓次数: {summary['dip_count']}")
    
    # 8. 保存结果到 CSV
    output_path = args.output or f"result_{args.fund}_{args.strategy}.csv"
    write_results_to_csv(results, output_path)
    print(f"\n✅ 详细结果已保存到: {output_path}")
    
    print("\n" + "=" * 60)


if __name__ == '__main__':
    main()

