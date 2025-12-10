"""
=============================================================================
                        基金定投回测 - 快速启动脚本
=============================================================================

使用方法：
    1. 修改下方的配置参数
    2. 直接运行：python run_backtest.py
"""

import sys
import os

# 添加项目根目录到 Python 路径
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, PROJECT_ROOT)

from core.backtest.engine import DataFeed, Portfolio, Backtester
from core.backtest.engine.backtester import write_results_to_csv
from core.backtest.utils.csv_loader import load_nav_series
from core.backtest.strategies.profit_recycle import ProfitRecycleStrategy
from core.backtest.strategies.pure_sip import PureSipStrategy
from core.backtest.strategies.ma_enhanced import MovingAverageEnhancedStrategy


# =============================================================================
#                              📊 基本配置
# =============================================================================

基金代码 = "163406"
净值数据文件 = "data/nav/163406.csv"
输出目录 = "data/results"


# =============================================================================
#                              💰 资金配置
# =============================================================================

初始投入金额 = 2000.0
每月定投金额 = 500.0


# =============================================================================
#                              📈 费率配置
# =============================================================================

买入费率 = 0.0012   # 0.12%
卖出费率 = 0.0      # 这里默认不考虑赎回费（纯定投对比用）


# =============================================================================
#                              🎯 策略选择
# =============================================================================
# 可选：
#   "profit_recycle" - 利润回收策略（你现在的 V6）
#   "pure_sip"       - 纯基础定投策略（基准线）
策略类型 = "profit_recycle"
# 策略类型 = "pure_sip"
# 策略类型 = "ma_enhanced"

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

    if 策略类型 == "profit_recycle":
        strategy = ProfitRecycleStrategy({})
        print(f"   策略名称: {strategy.get_name()}")
        print("   策略说明: 利润回收策略 — 止盈 + 盈利池补仓 + 深跌少量本金参与")
        print("   核心原则: 盈利回收到盈利池，逢低优先使用盈利池，深跌时按比例使用预投入本金池")

        levels = strategy.get_levels_info()
        print("\n   止盈档位（策略内置）:")
        for line in levels.get("tp_levels_desc", []):
            print(f"     - {line}")

        print("\n   补仓档位（策略内置）:")
        for line in levels.get("dip_levels_desc", []):
            print(f"     - {line}")

    elif 策略类型 == "pure_sip":
        strategy = PureSipStrategy({})
        print(f"   策略名称: {strategy.get_name()}")
        print("   策略说明: 纯基础定投策略 — 初始+每月定额全额买入，不止盈、不补仓")
        print("   核心原则: 不择时，只看长期收益，把它作为所有花样策略的对照基准")
        
    elif 策略类型 == "ma_enhanced":
        strategy = MovingAverageEnhancedStrategy({
            "base_amount": 每月定投金额,
            "ma_window": 250,
            "multiplier": 2.0,
            "min_factor": 0.0,
            "max_factor": 3.0,
        })
        print("   策略名称: MA250 均线增强定投策略")
        print("   策略说明: 高估少买，低估多买，不做止盈，只调节每次买入金额")
        print("   核心原则: 永远在场，利用估值波动优化买入时点")

    else:
        print(f"❌ 未知策略类型: {策略类型}")
        return

    print(f"\n💰 初始投入: {初始投入金额:,.2f} 元")
    print(f"💰 每月定投: {每月定投金额:,.2f} 元")
    print(f"📊 买入费率: {买入费率:.4%}")
    print(f"📊 卖出费率: {卖出费率:.4%}")

    # 4. 创建回测引擎
    backtester = Backtester(
        data_feed=data_feed,
        portfolio=portfolio,
        strategy=strategy,
        initial_invest=初始投入金额,
        periodic_invest=每月定投金额,
        invest_day_rule="month_change",
    )

    # 5. 执行回测
    print(f"\n⏳ 正在执行回测...")
    results = backtester.run()

    # 6. 输出结果（统一交给策略来渲染）
    summary = backtester.get_summary()

    print("\n" + "=" * 70)
    print("                         📊 回测结果（由策略输出）")
    print("=" * 70)

    strategy.render_summary(summary)

    print("\n" + "=" * 70)
    print(f"✅ 回测完成（{strategy.get_name()}.render_summary）")
    print("=" * 70)

    # 7. 保存结果
    output_file = os.path.join(output_dir, f"backtest_{基金代码}_{策略类型}.csv")
    write_results_to_csv(results, output_file)

    print("\n明细已写入 CSV 文件：")
    print(f"   {output_file}")


if __name__ == "__main__":
    run()
