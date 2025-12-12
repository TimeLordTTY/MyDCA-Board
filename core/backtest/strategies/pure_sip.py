# core/backtest/strategies/pure_sip.py

from typing import Any, Dict, Optional, List

from .base import Strategy, Context, Signal


class PureSipStrategy(Strategy):
    """
    纯基础定投策略（基准线）
    ------------------------
    - 初始资金：只要进了账户，就全额买入
    - 每月定投：资金打入后，只要在账户里，就全额买入
    - 任何形式的现金（分红、利息等）都不长期躺在现金池里
    - 不做止盈、不做补仓、不择时：只买不卖，长期持有

    可配置参数：
    ----------------------------------------------------------------
    - invest_all_cash: 是否将所有现金全额买入（默认 True）
    - allow_partial_invest: 是否允许部分投入（预留扩展，默认 False）
    - min_invest_amount: 最小投入金额（默认 0.0，即无限制）
    - reinvest_dividend: 是否自动再投资分红（默认 True）
    """

    # =======================================================
    # 默认配置值
    # =======================================================
    DEFAULT_INVEST_ALL_CASH = True        # 是否全额买入
    DEFAULT_ALLOW_PARTIAL_INVEST = False  # 是否允许部分投入
    DEFAULT_MIN_INVEST_AMOUNT = 0.0       # 最小投入金额
    DEFAULT_REINVEST_DIVIDEND = True      # 是否再投资分红

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config or {})
        
        # 加载可配置参数
        self.invest_all_cash: bool = bool(
            self.config.get("invest_all_cash", self.DEFAULT_INVEST_ALL_CASH)
        )
        self.allow_partial_invest: bool = bool(
            self.config.get("allow_partial_invest", self.DEFAULT_ALLOW_PARTIAL_INVEST)
        )
        self.min_invest_amount: float = float(
            self.config.get("min_invest_amount", self.DEFAULT_MIN_INVEST_AMOUNT)
        )
        self.reinvest_dividend: bool = bool(
            self.config.get("reinvest_dividend", self.DEFAULT_REINVEST_DIVIDEND)
        )

    # =======================================================
    # 生命周期
    # =======================================================
    def on_start(self) -> None:
        """
        初始化状态。
        """
        self.state["initialized"] = False
        self.state["total_invest_count"] = 0  # 投资次数统计

    def on_bar(self, ctx: Context) -> Signal:
        """
        每个交易日被回测引擎调用。

        纯定投策略规则：
        - 不区分"初始资金 / 每月定投 / 分红"：**只要 cash_pool > 0，就全额买入**
        - 这样可以自然实现：
            * 首日全额建仓
            * 每月定投资金到账后当日即买入
            * 分红现金自动再投资（避免现金滞留）
        """
        cash_pool = ctx.cash_pool
        cash_inflow = getattr(ctx, "cash_inflow", 0.0) or 0.0

        buy_cash = 0.0
        sell_units = 0.0
        note_parts: List[str] = []

        # 判断是否应该买入
        should_buy = False
        if self.invest_all_cash and cash_pool > 0:
            # 检查最小投入金额
            if cash_pool >= self.min_invest_amount:
                should_buy = True

        if should_buy:
            buy_cash = cash_pool
            self.state["total_invest_count"] = self.state.get("total_invest_count", 0) + 1

            # 日志仅用于区分场景，方便你在 CSV 里看
            if not self.state["initialized"]:
                self.state["initialized"] = True
                note_parts.append(f"首日建仓: 全额买入 {buy_cash:.2f} 元")
            elif cash_inflow and cash_inflow > 0:
                note_parts.append(f"月度定投: 全额买入 {buy_cash:.2f} 元")
            else:
                # 无 inflow，但 cash_pool > 0，多半是分红 / 零头
                if self.reinvest_dividend:
                    note_parts.append(f"红利/余额再投: 全额买入 {buy_cash:.2f} 元")
                else:
                    buy_cash = 0.0
                    note_parts.append(f"红利/余额: 不再投资，保留 {cash_pool:.2f} 元")

        note = "; ".join(note_parts)
        return Signal(buy_cash=buy_cash, sell_units=sell_units, note=note)

    # =======================================================
    # 统计 & 输出
    # =======================================================
    def get_stats(self) -> Dict[str, Any]:
        """
        返回策略专有统计信息，包含配置参数
        """
        return {
            # 配置参数
            "cfg_invest_all_cash": self.invest_all_cash,
            "cfg_allow_partial_invest": self.allow_partial_invest,
            "cfg_min_invest_amount": self.min_invest_amount,
            "cfg_reinvest_dividend": self.reinvest_dividend,
            # 运行时状态
            "total_invest_count": self.state.get("total_invest_count", 0),
        }

    def render_summary(self, summary: Dict[str, Any]) -> None:
        """
        由 run_backtest 调用，输出该策略视角下的回测总结。
        """
        print("\n" + "=" * 70)
        print("                         📊 回测结果（由策略输出）")
        print("=" * 70)
        
        # 基础信息
        strategy_name = summary.get("strategy_name", self.get_name())
        fund_code = summary.get("fund_code", "未知")
        start_date = summary.get("start_date")
        end_date = summary.get("end_date")
        days = summary.get("days", 0)
        
        print(f"\n【基础信息】")
        print(f"   策略名称: {strategy_name}")
        print(f"   基金代码: {fund_code}")
        print(f"   起止时间: {start_date} ~ {end_date}")
        print(f"   回测天数: {days} 天")
        
        # 策略配置参数
        print(f"\n【策略配置参数】")
        print(f"   全额买入 (invest_all_cash):         {self.invest_all_cash}")
        print(f"   允许部分投入 (allow_partial_invest): {self.allow_partial_invest}")
        print(f"   最小投入金额 (min_invest_amount):    {self.min_invest_amount:.2f} 元")
        print(f"   再投资分红 (reinvest_dividend):      {self.reinvest_dividend}")
        
        # 资金情况
        principal_total = summary.get("principal_total", 0.0)
        total_cost = summary.get("total_cost", 0.0)
        final_fund_value = summary.get("final_fund_value", 0.0)
        final_cash = summary.get("final_cash", 0.0)
        final_assets = summary.get("final_assets", 0.0)
        
        print(f"\n【资金情况】")
        print(f"   累计投入本金:  {principal_total:>12,.2f} 元")
        print(f"   实际买入成本:  {total_cost:>12,.2f} 元")
        print(f"   期末基金市值:  {final_fund_value:>12,.2f} 元")
        print(f"   期末现金余额:  {final_cash:>12,.2f} 元")
        print(f"   期末总资产:    {final_assets:>12,.2f} 元")
        
        # 收益情况
        nominal_pnl = summary.get("nominal_pnl", 0.0)
        nominal_return = summary.get("nominal_return", 0.0)
        real_return = summary.get("real_return", 0.0)
        annual_return = summary.get("annual_return", 0.0)
        
        print(f"\n【收益情况】")
        print(f"   名义盈亏金额(对下场资金):  {nominal_pnl:>+12,.2f} 元")
        print(f"   名义总收益率(对下场资金):  {nominal_return * 100:>12.2f}%")
        print(f"   真实总收益率(对全部本金):  {real_return * 100:>12.2f}%")
        print(f"   年化收益率:                {annual_return * 100:>12.2f}%")
        
        # 交易统计
        buy_count = summary.get("buy_count", 0)
        sell_count = summary.get("sell_count", 0)
        total_buy_amount = summary.get("total_buy_amount", 0.0)
        total_sell_amount = summary.get("total_sell_amount", 0.0)
        total_invest_count = summary.get("total_invest_count", self.state.get("total_invest_count", 0))
        
        print(f"\n【交易统计】")
        print(f"   买入次数: {buy_count}")
        print(f"   卖出次数: {sell_count}")
        print(f"   总买入金额:  {total_buy_amount:>12,.2f} 元")
        print(f"   总卖出金额:  {total_sell_amount:>12,.2f} 元")
        print(f"   投资操作次数: {total_invest_count}")
        
        print("\n" + "=" * 70)
        print(f"✅ 回测完成（{strategy_name}）")
        print("=" * 70)

