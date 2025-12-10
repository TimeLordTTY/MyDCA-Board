# core/backtest/strategies/pure_sip.py

from typing import Any, Dict

from .base import Strategy, Context, Signal


class PureSipStrategy(Strategy):
    """
    纯基础定投策略（基准线）
    ------------------------
    - 初始资金：只要进了账户，就全额买入
    - 每月定投：资金打入后，只要在账户里，就全额买入
    - 任何形式的现金（分红、利息等）都不长期躺在现金池里
    - 不做止盈、不做补仓、不择时：只买不卖，长期持有
    """

    def __init__(self, config: Dict[str, Any] | None = None):
        super().__init__(config)
        # 目前只有一个标记：是否已经发生过首次建仓（用于日志好看）
        self.state.setdefault("initialized", False)

    # =======================================================
    # 生命周期
    # =======================================================
    def on_start(self) -> None:
        """
        初始化状态。
        """
        self.state["initialized"] = False

    def on_bar(self, ctx: Context) -> Signal:
        """
        每个交易日被回测引擎调用。

        纯定投策略规则：
        - 不区分“初始资金 / 每月定投 / 分红”：**只要 cash_pool > 0，就全额买入**
        - 这样可以自然实现：
            * 首日全额建仓
            * 每月定投资金到账后当日即买入
            * 分红现金自动再投资（避免现金滞留）
        """
        cash_pool = ctx.cash_pool
        cash_inflow = getattr(ctx, "cash_inflow", 0.0)

        buy_cash = 0.0
        sell_units = 0.0
        note_parts: list[str] = []

        if cash_pool > 0:
            buy_cash = cash_pool

            # 日志仅用于区分场景，方便你在 CSV 里看
            if not self.state["initialized"]:
                self.state["initialized"] = True
                note_parts.append(f"首日建仓: 全额买入 {buy_cash:.2f} 元")
            elif cash_inflow and cash_inflow > 0:
                note_parts.append(f"月度定投: 全额买入 {buy_cash:.2f} 元")
            else:
                # 无 inflow，但 cash_pool > 0，多半是分红 / 零头
                note_parts.append(f"红利/余额再投: 全额买入 {buy_cash:.2f} 元")

        note = "; ".join(note_parts)
        return Signal(buy_cash=buy_cash, sell_units=sell_units, note=note)

    # =======================================================
    # 统计 & 输出
    # =======================================================
    def get_stats(self) -> Dict[str, Any]:
        """
        纯定投策略目前不维护额外资金池，直接返回空 dict。
        如果以后想记录“分红再投次数”等，可以从这里扩展。
        """
        return {}

    def render_summary(self, summary: Dict[str, Any]) -> None:
        """
        由 run_backtest 调用，输出该策略视角下的回测总结。
        """
        fund_code = summary.get("fund_code", "未知")
        start_date = summary.get("start_date")
        end_date = summary.get("end_date")
        days = summary.get("days", 0)

        total_cost = summary.get("total_cost", 0.0)
        final_fund_value = summary.get("final_fund_value", 0.0)
        final_cash = summary.get("final_cash", 0.0)
        final_value = summary.get("final_value", 0.0)
        total_return = summary.get("total_return", 0.0)
        annual_return = summary.get("annual_return", 0.0)

        buy_count = summary.get("buy_count", 0)
        sell_count = summary.get("sell_count", 0)
        total_buy = summary.get("total_buy", 0.0)
        total_sell = summary.get("total_sell", 0.0)

        profit = final_value - total_cost

        print()
        print("【基础信息】")
        print(f"   基金代码: {fund_code}")
        print(f"   起止时间: {start_date} ~ {end_date}")
        print(f"   回测天数: {days} 天")

        print()
        print("【资金情况】")
        print(f"   累计投入本金: {total_cost:>12,.2f} 元")
        print(f"   期末基金市值: {final_fund_value:>12,.2f} 元")
        print(f"   期末现金余额: {final_cash:>12,.2f} 元")
        print(f"   期末总资产:   {final_value:>12,.2f} 元")

        print()
        print("【收益情况】")
        print(f"   总盈亏金额:   {profit:>+12,.2f} 元")
        print(f"   总收益率:     {total_return:>+12.2%}")
        print(f"   年化收益率:   {annual_return:>+12.2%}")

        print()
        print("【交易统计】")
        print(f"   买入次数: {buy_count}")
        print(f"   卖出次数: {sell_count}")
        print(f"   总买入金额: {total_buy:,.2f} 元")
        print(f"   总卖出金额: {total_sell:,.2f} 元")

    def get_name(self) -> str:
        return "纯基础定投策略（全部现金自动进场）"
