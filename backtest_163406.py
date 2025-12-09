import pandas as pd
from datetime import timedelta

# ========= 1. 基本参数 =========

CSV_PATH = "fund_163406_nav.csv"   # 你的历史净值 CSV 路径
DATE_COL = "date"             # 日期列名
NAV_COL = "nav"               # 单位净值列名

YEARS = 10                    # 回测最近多少年

initial_investment = 2000.0   # 初始一次性投入（第一天全买入）
monthly_contribution = 500.0  # 每月追加的“工资定投额度”

tp_threshold = 0.20           # 止盈阈值：未实现收益率 >= 20% 时触发
tp_sell_ratio = 0.25          # 每次止盈卖出当前仓位的 25%

dip_threshold = 0.10          # 回撤阈值：相对历史高点回撤 10% 视为“跌深”
dip_buy_ratio = 0.40          # 每次用现金池的 40% 进行补仓
min_dip_cash = 0.0            # 触发补仓的最小现金门槛（可设 1000 之类）

OUTPUT_CSV = "backtest_163406_strategy.csv"


# ========= 2. 读取 & 预处理数据 =========

df = pd.read_csv(CSV_PATH)

# 日期解析与排序
df[DATE_COL] = pd.to_datetime(df[DATE_COL])
df = df.sort_values(DATE_COL).reset_index(drop=True)

# 截取最近 N 年
if YEARS is not None:
    end_date = df[DATE_COL].max()
    start_date = end_date - pd.DateOffset(years=YEARS)
    df = df[df[DATE_COL] >= start_date].reset_index(drop=True)

if df.empty:
    raise ValueError("筛选后的数据为空，检查 YEARS 或 CSV 文件内容。")

# ========= 3. 回测主逻辑 =========

units = 0.0             # 基金持有份额
fund_cost = 0.0         # 基金总成本（随着买入增加，卖出按均价扣减）
cash = 0.0              # 现金池
total_deposit = 0.0     # 总共打入系统的现金（初始+每月）

realized_profit = 0.0   # 已实现盈利
last_peak_nav = None    # 历史最高净值
prev_month = None       # 上一条记录所在的月份（year, month）

records = []            # 用来导出每日状态
tp_count = 0
dip_count = 0

for idx, row in df.iterrows():
    date = row[DATE_COL]
    nav = float(row[NAV_COL])
    month_key = (date.year, date.month)

    # 第一天：用 initial_investment 全额买入
    if idx == 0:
        cash += initial_investment
        total_deposit += initial_investment

        buy_cash = cash
        if buy_cash > 0:
            buy_units = buy_cash / nav
            units += buy_units
            fund_cost += buy_cash
            cash -= buy_cash

        prev_month = month_key
        last_peak_nav = nav

    # 每个新月份，往现金池打入一笔月度定投额度
    if idx > 0 and month_key != prev_month:
        cash += monthly_contribution
        total_deposit += monthly_contribution
        prev_month = month_key

    # 更新历史最高净值
    if last_peak_nav is None or nav > last_peak_nav:
        last_peak_nav = nav

    fund_value = units * nav
    total_value = fund_value + cash

    # 未实现收益率：只看基金仓位
    if fund_cost > 0:
        unrealized_gain = fund_value - fund_cost
        unrealized_gain_pct = unrealized_gain / fund_cost
    else:
        unrealized_gain = 0.0
        unrealized_gain_pct = 0.0

    # ========= 止盈逻辑 =========
    tp_sell_cash = 0.0
    if fund_cost > 0 and unrealized_gain_pct >= tp_threshold and units > 0:
        sell_units = units * tp_sell_ratio
        proceeds = sell_units * nav

        avg_cost_per_unit = fund_cost / units
        cost_removed = avg_cost_per_unit * sell_units

        fund_cost -= cost_removed
        units -= sell_units
        cash += proceeds
        realized_profit += (proceeds - cost_removed)
        tp_sell_cash = proceeds
        tp_count += 1
        note = f"止盈卖出 {sell_units:.2f} 份，回笼 {proceeds:.2f} 现金"
    else:
        note = ""

    # ========= 逢低补仓逻辑 =========
    drawdown = 0.0
    dip_buy_cash = 0.0

    if last_peak_nav is not None and last_peak_nav > 0:
        drawdown = (nav - last_peak_nav) / last_peak_nav  # 负数表示回撤

    if drawdown <= -dip_threshold and cash > min_dip_cash:
        buy_cash = cash * dip_buy_ratio
        if buy_cash > 0:
            buy_units = buy_cash / nav
            units += buy_units
            fund_cost += buy_cash
            cash -= buy_cash
            dip_buy_cash = buy_cash
            dip_count += 1
            if note:
                note += "；"
            note += f"回撤 {drawdown*100:.2f}%，逢低买入 {buy_units:.2f} 份，花费 {buy_cash:.2f}"
    
    # 重新计算最新状态
    fund_value = units * nav
    total_value = fund_value + cash

    records.append({
        "date": date,
        "nav": nav,
        "units": units,
        "fund_value": fund_value,
        "cash": cash,
        "total_value": total_value,
        "fund_cost": fund_cost,
        "total_deposit": total_deposit,
        "unrealized_gain": fund_value - fund_cost,
        "unrealized_gain_pct": (fund_value - fund_cost) / fund_cost * 100 if fund_cost > 0 else 0.0,
        "drawdown_pct": drawdown * 100,
        "last_peak_nav": last_peak_nav,
        "tp_sell_cash": tp_sell_cash,
        "dip_buy_cash": dip_buy_cash,
        "note": note
    })

# ========= 4. 输出结果 =========

result_df = pd.DataFrame(records)
result_df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")

start_date = result_df["date"].iloc[0]
end_date = result_df["date"].iloc[-1]
months = (end_date.year - start_date.year) * 12 + (end_date.month - start_date.month) + 1

final_fund = result_df["fund_value"].iloc[-1]
final_cash = result_df["cash"].iloc[-1]
final_total = result_df["total_value"].iloc[-1]

total_return = (final_total - total_deposit) / total_deposit if total_deposit > 0 else 0.0
annualized_return = (1 + total_return) ** (12 / months) - 1 if months > 0 else 0.0

print("====== 兴全合润 10 年策略回测结果 ======")
print(f"起止时间：{start_date.date()} ~ {end_date.date()}（共约 {months} 个月）")
print(f"总投入（初始 + 每月）: {total_deposit:.2f}")
print(f"最终基金市值：      {final_fund:.2f}")
print(f"最终现金池余额：    {final_cash:.2f}")
print(f"组合总资产：        {final_total:.2f}")
print(f"总收益率：          {total_return*100:.2f}%")
print(f"折算年化收益率：    {annualized_return*100:.2f}%")
print(f"止盈次数：          {tp_count}")
print(f"逢低补仓次数：      {dip_count}")
print(f"明细已写入：        {OUTPUT_CSV}")