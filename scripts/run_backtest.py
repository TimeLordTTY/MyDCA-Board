"""
=============================================================================
                        基金定投回测 - 快速启动脚本
=============================================================================

使用方法：
    1. 修改下方的配置参数
    2. 直接运行：python run_backtest.py

注意：这是一个临时脚本，以后会通过页面或 API 触发回测
"""

import sys
import os

# 添加项目根目录到 Python 路径
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)

from core.backtest.engine import DataFeed, Portfolio, Backtester
from core.backtest.engine.backtester import write_results_to_csv
from core.backtest.utils.csv_loader import load_nav_series
from core.backtest.strategies import SipStrategy, TpDipStrategy


# =============================================================================
#                              📊 基本配置
# =============================================================================

# 基金代码（用于标记输出文件）
基金代码 = "163406"

# 净值数据文件路径（相对于项目根目录）
# 可以使用 scripts/download_nav.py 下载数据
# 示例：python download_nav.py 163406
净值数据文件 = "data/nav/163406.csv"

# 回测结果输出目录
输出目录 = "data/results"


# =============================================================================
#                              💰 资金配置
# =============================================================================

# 初始一次性投入金额（第一天买入）
初始投入金额 = 10000.0

# 每月定投金额
每月定投金额 = 1000.0


# =============================================================================
#                              📈 费率配置
# =============================================================================

# 买入手续费率（0.0015 = 0.15%）
买入费率 = 0.0015

# 卖出手续费率（0.005 = 0.5%）
卖出费率 = 0.005


# =============================================================================
#                              🎯 策略选择
# =============================================================================

# 策略类型：
#   "sip"     - 普通定投（每月定额买入，不止盈不补仓）
#   "tp_dip"  - 止盈补仓（多档止盈 + 多档逢低加仓）
策略类型 = "tp_dip"


# =============================================================================
#                         🔧 止盈补仓策略配置（仅 tp_dip 生效）
# =============================================================================

# 止盈档位配置
# - threshold: 收益率阈值（0.10 = 10%）
# - sell_ratio: 触发后卖出的比例（0.25 = 卖出25%持仓）
止盈档位 = [
    {"threshold": 0.10, "sell_ratio": 0.25},   # 收益率达 10%，卖出 25%
    {"threshold": 0.20, "sell_ratio": 0.25},   # 收益率达 20%，卖出 25%
    {"threshold": 0.30, "sell_ratio": 0.50},   # 收益率达 30%，卖出 50%
]

# 补仓档位配置
# - drawdown: 从历史高点回撤的比例（0.05 = 回撤5%）
# - extra_amount: 触发后额外买入的金额
补仓档位 = [
    {"drawdown": 0.05, "extra_amount": 500},   # 回撤 5%，补仓 500 元
    {"drawdown": 0.10, "extra_amount": 1000},  # 回撤 10%，补仓 1000 元
    {"drawdown": 0.15, "extra_amount": 1500},  # 回撤 15%，补仓 1500 元
]

# 单次补仓最多使用现金池的比例（1.0 = 100%）
单次补仓最大比例 = 1.0

# 是否自动投资每月新增资金
# True: 每月定投资金会自动买入（即使没触发补仓）
# False: 每月资金只进入现金池，等待补仓条件触发
自动投资新增资金 = True


# =============================================================================
#                              🚀 执行回测
# =============================================================================

def run():
    """执行回测"""
    
    print("=" * 70)
    print("                    基金定投策略回测")
    print("=" * 70)
    
    # 构建完整路径
    csv_path = os.path.join(PROJECT_ROOT, 净值数据文件)
    output_dir = os.path.join(PROJECT_ROOT, 输出目录)
    os.makedirs(output_dir, exist_ok=True)
    
    # 1. 加载数据
    print(f"\n📊 基金代码: {基金代码}")
    print(f"📁 数据文件: {净值数据文件}")
    
    if not os.path.exists(csv_path):
        print(f"\n❌ 错误：找不到净值数据文件: {csv_path}")
        print(f"💡 提示：请先运行下载脚本:")
        print(f"   cd scripts")
        print(f"   python download_nav.py {基金代码}")
        return
    
    try:
        bars = load_nav_series(csv_path)
        print(f"   数据条数: {len(bars)} 条")
        print(f"   时间范围: {bars[0].date.strftime('%Y-%m-%d')} ~ {bars[-1].date.strftime('%Y-%m-%d')}")
    except Exception as e:
        print(f"❌ 数据加载失败: {e}")
        return
    
    # 2. 创建数据源和组合
    data_feed = DataFeed(bars)
    portfolio = Portfolio(
        cash=0.0,
        buy_fee_rate=买入费率,
        sell_fee_rate=卖出费率
    )
    
    # 3. 创建策略
    print(f"\n📈 策略类型: {策略类型}")
    print(f"💰 初始投入: {初始投入金额:,.2f} 元")
    print(f"💰 每月定投: {每月定投金额:,.2f} 元")
    print(f"📊 买入费率: {买入费率:.4%}")
    print(f"📊 卖出费率: {卖出费率:.4%}")
    
    if 策略类型 == "sip":
        strategy = SipStrategy({
            'immediate_invest': True,
        })
        print("   策略说明: 每月定额买入，不止盈不补仓")
        
    elif 策略类型 == "tp_dip":
        strategy = TpDipStrategy({
            'tp_levels': 止盈档位,
            'dip_levels': 补仓档位,
            'max_dip_buy_ratio_of_cash': 单次补仓最大比例,
            'tp_reference': 'cost',
            'auto_invest_inflow': 自动投资新增资金,
        })
        print("   策略说明: 多档止盈 + 多档逢低加仓")
        print(f"\n   止盈档位:")
        for level in 止盈档位:
            print(f"     - 收益率 ≥ {level['threshold']:.0%} → 卖出 {level['sell_ratio']:.0%}")
        print(f"\n   补仓档位:")
        for level in 补仓档位:
            print(f"     - 回撤 ≥ {level['drawdown']:.0%} → 补仓 {level['extra_amount']:.0f} 元")
    else:
        print(f"❌ 未知策略类型: {策略类型}")
        return
    
    # 4. 创建回测引擎
    backtester = Backtester(
        data_feed=data_feed,
        portfolio=portfolio,
        strategy=strategy,
        initial_invest=初始投入金额,
        periodic_invest=每月定投金额,
        invest_day_rule="month_change"
    )
    
    # 5. 执行回测
    print(f"\n⏳ 正在执行回测...")
    results = backtester.run()
    
    # 6. 输出结果
    summary = backtester.get_summary()
    
    print("\n" + "=" * 70)
    print("                         📊 回测结果")
    print("=" * 70)
    
    print(f"\n【时间区间】")
    print(f"   起止时间: {summary['start_date']} ~ {summary['end_date']}")
    print(f"   回测天数: {summary['days']} 天")
    
    print(f"\n【资金情况】")
    print(f"   累计投入本金: {summary['total_cost']:>12,.2f} 元")
    print(f"   期末基金市值: {summary['final_fund_value']:>12,.2f} 元")
    print(f"   期末现金余额: {summary['final_cash']:>12,.2f} 元")
    print(f"   期末总资产:   {summary['final_value']:>12,.2f} 元")
    
    print(f"\n【收益情况】")
    profit = summary['final_value'] - summary['total_cost']
    print(f"   总盈亏金额:   {profit:>+12,.2f} 元")
    print(f"   总收益率:     {summary['total_return']:>+12.2%}")
    print(f"   年化收益率:   {summary['annual_return']:>+12.2%}")
    
    print(f"\n【交易统计】")
    print(f"   买入次数: {summary['buy_count']}")
    print(f"   卖出次数: {summary['sell_count']}")
    print(f"   总买入金额: {summary['total_buy']:,.2f} 元")
    print(f"   总卖出金额: {summary['total_sell']:,.2f} 元")
    
    if 'tp_count' in summary:
        print(f"   止盈触发次数: {summary['tp_count']}")
    if 'dip_count' in summary:
        print(f"   补仓触发次数: {summary['dip_count']}")
    
    # 7. 保存结果
    output_file = os.path.join(output_dir, f"backtest_{基金代码}_{策略类型}.csv")
    write_results_to_csv(results, output_file)
    
    print(f"\n" + "=" * 70)
    print(f"✅ 回测完成！详细结果已保存到:")
    print(f"   {output_file}")
    print("=" * 70)


if __name__ == "__main__":
    run()


