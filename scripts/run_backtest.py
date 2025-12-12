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
from core.backtest.strategies import STRATEGY_REGISTRY


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
# 策略名称（必填）
# 可选：
#   "profit_recycle" - 利润回收策略
#   "pure_sip"       - 纯基础定投策略（基准线）
#   "ma_enhanced"    - MA均线增强定投策略
策略类型 = "profit_recycle"

# 策略版本（可选，None 则使用默认版本）
# profit_recycle 可用版本: "v10", 或 None (默认)
# pure_sip 可用版本: None (仅一个版本)
# ma_enhanced 可用版本: None (仅一个版本)
策略版本 = "v10"  # None 表示使用默认版本


# =============================================================================
#                              ⚙️ 策略参数配置
# =============================================================================
# 各策略的可配置参数，设置为 None 则使用默认值
# ---------- 利润回收策略 (profit_recycle) 参数 V10 ----------
# 目标：在 163406 上相比纯定投形成“可见的超额收益”，同时逻辑仍然现实可执行
利润回收策略参数 = {
    # ========== 基础参数 ==========
    # 1) 固定锁定比例（动态预投入启用时作为兜底 & 中性区参考）
    "pre_invest_ratio": 0.05,        # 中性区：默认每笔锁 5%

    # 2) 单一阈值模式参数（multi_stage_thresholds 为空时使用）
    #    这里只是兜底，实际会被多级阈值覆盖
    "deep_dip_threshold": -0.10,     # 备用：回撤 10% 触发
    "deep_dip_use_ratio": 0.50,      # 备用：释放 50%

    # 3) 重置与连发控制
    "reset_on_new_high": True,       # 净值创新高时重置深跌标记
    "allow_multi_deep_dip": False,   # 关闭“无脑连发”，交给连发机制控制频率

    # ========== 连发机制参数 ==========
    "rebound_reset_rate": 0.05,      # 从上次补仓价位反弹 5% 即可重置
    "debounce_days": 30,             # 或者横盘超过 30 天也重置

    # ========== 多级阈值模式 ==========
    # 对齐你和 Gemini 说的：
    #   - 跌 10% 补一半
    #   - 跌 15% 全补完
    "multi_stage_thresholds": [
        {"threshold": -0.10, "use_ratio": 0.50},  # 回撤 ≥10%，释放 50% 锁定资金
        {"threshold": -0.15, "use_ratio": 1.00},  # 回撤 ≥15%，释放剩余全部锁定资金
    ],

    # ========== 动态预投入 ==========
    #   - MA250 下方视为低位：不锁钱，全部买入
    #   - MA250~MA250*1.2 视为中性：每笔锁 5%
    #   - 高于 MA250*1.2 视为高位：每笔锁 20% 等待未来深跌
    "dynamic_pre_invest": {
        "enabled": True,         # 打开动态预投入开关
        "ma_window": 250,        # 使用 250 日均线
        "low_zone_bias": 0.0,    # nav <= MA -> 低位
        "high_zone_bias": 0.20,  # nav >= MA * 1.2 -> 高位

        "low_ratio": 0.0,        # 低位不锁钱（全部进场）
        "normal_ratio": 0.05,    # 中性区锁 5%
        "high_ratio": 0.20,      # 高位锁 20% 做子弹
    },
}

# ---------- 纯定投策略 (pure_sip) 参数 ----------
纯定投策略参数 = {
    "invest_all_cash": True,         # 是否全额买入（默认 True）
    "allow_partial_invest": False,   # 是否允许部分投入（默认 False）
    "min_invest_amount": 0.0,        # 最小投入金额（默认 0.0）
    "reinvest_dividend": True,       # 是否再投资分红（默认 True）
}

# ---------- MA均线增强策略 (ma_enhanced) 参数 ----------
MA均线策略参数 = {
    "ma_window": 250,                # 均线窗口长度（默认 250）
    "base_amount": 每月定投金额,      # 每月基础定投金额
    "multiplier": 2.0,               # 偏离度放大倍数（默认 2.0）
    "min_factor": 0.0,               # 最小买入因子（默认 0.3，设为 0 可完全不买）
    "max_factor": 3.0,               # 最大买入因子（默认 3.0）
    "allow_non_invest_day_buy": True,  # 非定投日是否允许抄底（默认 True）
    "non_invest_day_threshold": 1.0,   # 非定投日抄底阈值（默认 1.0）
    "first_day_full_invest": True,     # 首日是否全仓买入（默认 True）
}


# =============================================================================
#                              📅 回测日期区间配置
# =============================================================================
# 自定义回测开始和结束日期（格式：YYYY-MM-DD）
# 设置为 None 则使用数据文件的全部时间范围
回测起始日期 = "2020-12-01"
回测结束日期 = "2025-12-01"
# 回测起始日期 = None
# 回测结束日期 = None

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

    # 3. 创建策略（使用策略注册表加载）
    print(f"\n📈 策略类型: {策略类型}")
    if 策略版本:
        print(f"   策略版本: {策略版本}")
    
    try:
        # 从注册表获取策略类
        strategy_class = STRATEGY_REGISTRY.get(策略类型, 策略版本)
        
        # 根据策略类型选择配置参数
        if 策略类型 == "profit_recycle":
            strategy_config = 利润回收策略参数
        elif 策略类型 == "pure_sip":
            strategy_config = 纯定投策略参数
        elif 策略类型 == "ma_enhanced":
            strategy_config = MA均线策略参数
        else:
            strategy_config = {}
        
        # 实例化策略
        strategy = strategy_class(strategy_config)
        
        # 显示策略信息
        print(f"   策略名称: {strategy.get_name()}")
        print(f"   策略标识: {strategy.get_strategy_key()}")
        print(f"   策略版本: {strategy.get_version()}")
        
        # 显示策略说明
        if 策略类型 == "profit_recycle":
            print("   策略说明: 利润回收策略 — 预投入锁定 + 深跌补仓")
            print("   核心原则: 抽取部分本金形成子弹，深跌时集中释放")
            
            if hasattr(strategy, 'get_levels_info'):
                levels = strategy.get_levels_info()
                print("\n   止盈档位（策略内置）:")
                for line in levels.get("tp_levels_desc", []):
                    print(f"     - {line}")
                
                print("\n   补仓档位（策略内置）:")
                for line in levels.get("dip_levels_desc", []):
                    print(f"     - {line}")
        
        elif 策略类型 == "pure_sip":
            print("   策略说明: 纯基础定投策略 — 初始+每月定额全额买入，不止盈、不补仓")
            print("   核心原则: 不择时，只看长期收益，把它作为所有花样策略的对照基准")
        
        elif 策略类型 == "ma_enhanced":
            print("   策略说明: 高估少买，低估多买，不做止盈，只调节每次买入金额")
            print("   核心原则: 永远在场，利用估值波动优化买入时点")
    
    except ValueError as e:
        print(f"❌ 策略加载失败: {e}")
        print("\n可用策略及版本:")
        for name, versions in STRATEGY_REGISTRY.list_strategies().items():
            print(f"   {name}: {versions}")
        return

    print(f"\n💰 初始投入: {初始投入金额:,.2f} 元")
    print(f"💰 每月定投: {每月定投金额:,.2f} 元")
    print(f"📊 买入费率: {买入费率:.4%}")
    print(f"📊 卖出费率: {卖出费率:.4%}")
    
    # 显示日期区间配置
    if 回测起始日期 or 回测结束日期:
        print(f"\n📅 自定义回测区间:")
        if 回测起始日期:
            print(f"   起始日期: {回测起始日期}")
        if 回测结束日期:
            print(f"   结束日期: {回测结束日期}")

    # 4. 创建回测引擎
    backtester = Backtester(
        data_feed=data_feed,
        portfolio=portfolio,
        strategy=strategy,
        initial_invest=初始投入金额,
        periodic_invest=每月定投金额,
        invest_day_rule="month_change",
        start_date=回测起始日期,
        end_date=回测结束日期,
        fund_code=基金代码,
    )

    # 5. 执行回测
    print(f"\n⏳ 正在执行回测...")
    results = backtester.run()

    # 6. 输出结果
    summary = backtester.get_summary()
    
    # 显示日期区间信息（仅在有自定义区间时显示）
    if 回测起始日期 or 回测结束日期:
        print("\n" + "=" * 70)
        print("                         📊 数据区间信息")
        print("=" * 70)
        print(f"\n【数据区间】")
        print(f"   原始数据区间: {summary.get('data_start_date', 'N/A')} ~ {summary.get('data_end_date', 'N/A')}")
        print(f"   实际回测区间: {summary.get('backtest_start_date', 'N/A')} ~ {summary.get('backtest_end_date', 'N/A')}")
    
    # 调用策略的 render_summary（只调用一次，由策略负责所有输出）
    strategy.render_summary(summary)

    # 7. 输出结果表格并保存 CSV
    export_result = backtester.export_results(
        output_dir=output_dir,
        prefix=f"backtest_{基金代码}_{策略类型}",
        fund_name="",  # 可以从基金信息中获取
        print_to_console=False,  # 控制台不输出表格
        save_to_csv=True,
    )

    print("\n📁 已生成文件：")
    for key, filepath in export_result.get("files", {}).items():
        if isinstance(filepath, list):
            for fp in filepath:
                print(f"   - {fp}")
        else:
            print(f"   - {filepath}")


if __name__ == "__main__":
    run()
