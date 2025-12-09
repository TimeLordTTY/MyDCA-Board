"""
基金模拟器

使用随机收益率模拟基金净值走势，测试定投策略效果
"""

import csv
import math
import random
import os

# 获取项目根目录
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def simulate_fund(
    months=24,
    # === 基金本身参数 ===
    initial_nav=1.0,          # 初始净值
    initial_units=2000.0,     # 初始持有份额（类似你一开始投了 2000 元）
    # === 市场走势参数（随机生成用的） ===
    mean_return_annual=0.10,  # 年化期望收益率（比如 10%）
    vol_annual=0.18,          # 年化波动率（比如 18%）
    # === 你的现金流参数 ===
    monthly_contribution=500.0,  # 每月定投固定往“现金池”里放多少钱
    # === 低位补仓策略 ===
    dip_threshold=0.07,       # 从历史高点回撤多少算“大跌买入”（比如 7%）
    dip_buy_fraction=0.5,     # 触发大跌时，用现金池多少比例去买入（0.5 = 用 50% 现金补仓）
    # === 高位止盈策略 ===
    tp_threshold=0.20,        # 累计浮盈达到多少比例开始止盈（比如 0.2 = 20%）
    tp_sell_fraction=0.25,    # 止盈时卖出多少份额（占当前总份额的比例）
    # === 随机种子（保证复现） ===
    seed=42,
):
    """
    核心模拟函数：
    - 用随机收益率大致模拟一支基金；
    - 执行：每月定投 + 大跌补仓 + 高位止盈；
    - 返回每个月的详细状态记录（字典列表）。
    """
    random.seed(seed)

    # 年化 → 月度（简单换算，接近现实）
    mean_r_monthly = (1 + mean_return_annual) ** (1 / 12) - 1
    vol_monthly = vol_annual / math.sqrt(12)

    # 初始状态
    nav = initial_nav
    units = initial_units
    total_cost = initial_nav * initial_units  # 总本金成本
    last_peak_nav = nav                       # 历史最高净值
    cash = 0.0                                # 现金池（留给下次补仓的）

    history = []

    for m in range(1, months + 1):
        # 1. 本月工资定投：先不直接买基金，而是先进“现金池”
        cash += monthly_contribution

        # 2. 生成本月的随机涨跌（正态分布，做一个「大致像兴全」的模型）
        r = random.gauss(mean_r_monthly, vol_monthly)
        # 防止极端值，截断一下
        r = max(min(r, 0.30), -0.30)

        # 3. 按月度收益更新净值
        prev_nav = nav
        nav = nav * (1 + r)

        # 更新历史最高净值
        if nav > last_peak_nav:
            last_peak_nav = nav

        # 准备记录本月发生的操作
        action_notes = []

        # 4. 低位补仓逻辑：如果从历史高点回撤超过 dip_threshold，则用现金的一部分买入
        dip_buy_cash = 0.0
        dip_buy_units = 0.0
        if last_peak_nav > 0:
            drawdown = (last_peak_nav - nav) / last_peak_nav  # 回撤比例

            if drawdown >= dip_threshold and cash > 0:
                # 本月用于补仓的金额：用现金池的一部分
                dip_buy_cash = cash * dip_buy_fraction
                dip_buy_units = dip_buy_cash / nav

                units += dip_buy_units
                total_cost += dip_buy_cash
                cash -= dip_buy_cash

                action_notes.append(
                    f"低位补仓: 用现金 {dip_buy_cash:.2f} 买入 {dip_buy_units:.2f} 份"
                )

        # 5. 计算当前基金市值、浮盈
        fund_value = nav * units
        unrealized_gain = fund_value - total_cost
        unrealized_gain_pct = (
            unrealized_gain / total_cost if total_cost > 0 else 0.0
        )

        # 6. 高位止盈逻辑：浮盈比例超过 tp_threshold，就卖出一部分份额
        tp_sell_units = 0.0
        tp_sell_cash = 0.0
        if unrealized_gain_pct >= tp_threshold and units > 0:
            tp_sell_units = units * tp_sell_fraction
            tp_sell_cash = tp_sell_units * nav

            # 卖出：份额减少，现金增加
            units -= tp_sell_units
            cash += tp_sell_cash

            # 本金成本按比例下降（相当于“本金 + 盈利”一起卖出一部分）
            total_cost *= (1 - tp_sell_fraction)

            # 重新计算市值和浮盈
            fund_value = nav * units
            unrealized_gain = fund_value - total_cost
            unrealized_gain_pct = (
                unrealized_gain / total_cost if total_cost > 0 else 0.0
            )

            action_notes.append(
                f"高位止盈: 卖出 {tp_sell_units:.2f} 份, 回收现金 {tp_sell_cash:.2f}"
            )

        total_value = cash + fund_value  # 基金 + 现金 的总财富

        # 记录本月状态
        history.append(
            {
                "month": m,
                "monthly_return(%)": round(r * 100, 2),
                "nav": round(nav, 4),
                "units": round(units, 4),
                "fund_value": round(fund_value, 2),
                "cash_pool": round(cash, 2),
                "total_value": round(total_value, 2),
                "total_cost": round(total_cost, 2),
                "unrealized_gain": round(unrealized_gain, 2),
                "unrealized_gain_pct(%)": round(unrealized_gain_pct * 100, 2),
                "last_peak_nav": round(last_peak_nav, 4),
                "dip_buy_cash": round(dip_buy_cash, 2),
                "tp_sell_cash": round(tp_sell_cash, 2),
                "note": " | ".join(action_notes),
            }
        )

    return history


def save_history_to_csv(history, filename="simulation_result.csv"):
    """保存模拟结果到 data/results/ 目录"""
    if not history:
        print("没有模拟数据可写入。")
        return

    # 输出到 data/results/ 目录
    output_dir = os.path.join(PROJECT_ROOT, "data", "results")
    os.makedirs(output_dir, exist_ok=True)
    filepath = os.path.join(output_dir, filename)

    fieldnames = list(history[0].keys())

    with open(filepath, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in history:
            writer.writerow(row)

    print(f"✅ 模拟结果已写入：{filepath}")


if __name__ == "__main__":
    # 你可以在这里改参数：
    history = simulate_fund(
        months=24,              # 模拟 24 个月，你可以改成 36 / 60 / 120...
        initial_nav=1.0,        # 初始净值
        initial_units=2000.0,   # 初始份额≈你最开始那 2000 元
        monthly_contribution=500.0,  # 每月往“现金池”塞 500
        mean_return_annual=0.10,     # 期望年化 10%
        vol_annual=0.18,             # 波动率 18%
        dip_threshold=0.07,          # 从历史高点跌 7% 就算“大跌”
        dip_buy_fraction=0.5,        # 大跌时用现金池 50% 来补仓
        tp_threshold=0.20,           # 浮盈达到 20% 开始考虑止盈
        tp_sell_fraction=0.25,       # 止盈时卖出 25% 份额
        seed=42,                     # 随机种子，改成别的数字能看到别的轨迹
    )

    save_history_to_csv(history)