from typing import Dict, Any
from .base import Strategy, Context, Signal


class ProfitRecycleStrategy(Strategy):
    """
    利润回收策略 v7（基线版）
    ==========================
    目标：先验证“引擎 + 资金池记账”是正确的，再谈花活。

    当前版本的行为刻意设计成几乎等同于基础定投：

    - 初始投入 + 每月定投：全部进场买入
    - 遇到分红 / 零头现金：也全部买入（红利再投）
    - 不止盈、不补仓、不择时（全部逻辑关闭）
    - 只做：把钱全丢进基金里，然后老老实实拿着

    同时，内部继续维护这些状态，方便后续版本启用：
      - principal_total          : 累计打入的本金总额（初始 + 每月）
      - pre_invest_pool          : 预投入本金池（当前版本始终保持 0）
      - redeem_profit_pool       : 赎回盈利池（当前版本始终保持 0）
      - redeem_principal_pool    : 累计赎回本金（当前版本保持 0）
      - last_peak_nav            : 观测用最高净值（暂不参与决策）
    """

    def __init__(self, config: Dict[str, Any] | None = None) -> None:
        super().__init__(config or {})

    # =======================================================
    # 生命周期
    # =======================================================
    def on_start(self) -> None:
        """初始化策略状态"""
        self.state["initialized"] = False

        # 资金相关
        self.state["principal_total"] = 0.0          # 真实打入系统的本金总额
        self.state["pre_invest_pool"] = 0.0          # 预投入本金池（当前版本不用）
        self.state["redeem_profit_pool"] = 0.0       # 赎回盈利池（当前版本不用）
        self.state["redeem_principal_pool"] = 0.0    # 累计赎回的本金（当前版本不用）

        # 观测用信息
        self.state["last_peak_nav"] = None           # 最高净值，仅记录不参与决策

    # =======================================================
    # 主逻辑：每天只做一件事——把现金全买掉
    # =======================================================
    def on_bar(self, ctx: Context) -> Signal:
        """
        纯基础定投等价行为：

        - 任何时候，只要 cash_pool > 0，就全部买入；
        - 不做卖出，不做止盈，不做补仓；
        - 同时记录 principal_total，方便和 backtester 的 total_cost 对账。
        """
        date = ctx.date
        nav = ctx.nav

        cash_pool: float = ctx.cash_pool
        cash_inflow: float = float(getattr(ctx, "cash_inflow", 0.0))

        buy_cash = 0.0
        sell_units = 0.0
        notes: list[str] = []

        # 1) 记录今日新打入的本金（初始 + 每月定投）
        if cash_inflow > 0:
            self.state["principal_total"] += cash_inflow

        # 2) 只要账户里有现金，就全部买入
        if cash_pool > 0:
            buy_cash = cash_pool

            if not self.state["initialized"]:
                self.state["initialized"] = True
                notes.append(f"{date} 首日建仓: 全额买入 {buy_cash:.2f} 元")
            elif cash_inflow > 0:
                notes.append(f"{date} 月度定投+红利再投: 买入 {buy_cash:.2f} 元")
            else:
                notes.append(f"{date} 红利/余额再投: 买入 {buy_cash:.2f} 元")
        else:
            # 第一天可能恰好没有现金，也标记为已初始化，避免后续日志混乱
            if not self.state["initialized"]:
                self.state["initialized"] = True

        # 3) 最高净值仅做观测记录（暂不参与决策）
        if self.state["last_peak_nav"] is None or nav > self.state["last_peak_nav"]:
            self.state["last_peak_nav"] = nav

        return Signal(
            buy_cash=buy_cash,
            sell_units=sell_units,
            note="; ".join(notes),
        )

    # =======================================================
    # 档位信息（当前版本无止盈/无补仓，仅说明用途）
    # =======================================================
    def get_levels_info(self) -> Dict[str, Any]:
        """
        返回策略的“档位”说明，供 run_backtest 做展示。
        当前 v7 版本是基线模式：无止盈、无补仓，只做全额定投。
        """
        return {
            "mode": "baseline",
            "take_profit_levels": [],
            "dip_buy_levels": [],
            "description": "v7 基线版：不止盈、不补仓，所有现金全额买入，用于对标纯定投策略。",
        }

    # =======================================================
    # 统计 & 名称
    # =======================================================
    def get_stats(self) -> Dict[str, Any]:
        """供外部或 render_summary 使用的内部统计信息"""
        return {
            "principal_total": self.state.get("principal_total", 0.0),
            "pre_invest_pool": self.state.get("pre_invest_pool", 0.0),
            "redeem_profit_pool": self.state.get("redeem_profit_pool", 0.0),
            "redeem_principal_pool": self.state.get("redeem_principal_pool", 0.0),
            "last_peak_nav": self.state.get("last_peak_nav", 0.0),
        }

    def get_name(self) -> str:
        return "利润回收策略 v7（基线版）"

    def get_description(self) -> str:
        return (
            "利润回收策略 v7（基线版）— 当前版本刻意退化为“纯基础定投行为”，"
            "只做：初始 + 每月定投 + 红利全额买入，不做止盈、不做补仓，"
            "用于验证引擎与资金池记账的正确性。"
        )

    # =======================================================
    # 策略自己的汇总打印逻辑（run_backtest 会调用这个）
    # =======================================================
    def render_summary(self, summary: Dict[str, Any]) -> None:
        stats = self.get_stats()

        fund_code = summary.get("fund_code", "未知")
        start_date = summary.get("start_date")
        end_date = summary.get("end_date")
        days = summary.get("days")

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

        print("\n" + "=" * 70)
        print("                         📊 回测结果（由策略输出）")
        print("=" * 70)

        print("\n【基础信息】")
        print(f"   基金代码: {fund_code}")
        print(f"   起止时间: {start_date} ~ {end_date}")
        print(f"   回测天数: {days} 天")

        print("\n【资金情况】")
        print(f"   累计投入本金: {total_cost:>12,.2f} 元")
        print(f"   期末基金市值: {final_fund_value:>12,.2f} 元")
        print(f"   期末现金余额: {final_cash:>12,.2f} 元")
        print(f"   期末总资产:   {final_value:>12,.2f} 元")

        print("\n【收益情况】")
        print(f"   总盈亏金额:   {profit:>+12,.2f} 元")
        print(f"   总收益率:     {total_return:>+12.2%}")
        print(f"   年化收益率:   {annual_return:>+12.2%}")

        print("\n【交易统计】")
        print(f"   买入次数: {buy_count}")
        print(f"   卖出次数: {sell_count}")
        print(f"   总买入金额: {total_buy:,.2f} 元")
        print(f"   总卖出金额: {total_sell:,.2f} 元")

        print("\n【内部资金池状态】（当前版本仅做记录，不参与决策）")
        print(f"   累计打入本金总额 (principal_total): {stats['principal_total']:>12,.2f} 元")
        print(f"   预投入本金池 (pre_invest_pool):     {stats['pre_invest_pool']:>12,.2f} 元")
        print(f"   赎回盈利池   (redeem_profit_pool):  {stats['redeem_profit_pool']:>12,.2f} 元")
        print(f"   累计赎回本金 (redeem_principal_pool): {stats['redeem_principal_pool']:>12,.2f} 元")
        print(f"   观测最高净值 (last_peak_nav):       {stats['last_peak_nav']:>12,.4f}")

        print("\n" + "=" * 70)
        print("✅ 回测完成（利润回收策略 v7 — 基线版）")
        print("=" * 70 + "\n")
