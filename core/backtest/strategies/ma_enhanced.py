from typing import Dict, Any, Optional, List

from .base import Strategy, Context, Signal


class MovingAverageEnhancedStrategy(Strategy):
    """
    MA250 均线增强定投策略 v2.1

    核心思想：
    ----------------------------------------------------------------
    - 永远以「纯定投」为基线：长期持有优质基金，不轻易卖出；
    - 在此基础上，仅调整「每次买多少」：
        * 低估（跌破年线、或明显低估）时：多买；
        * 高估（远高于年线）时：少买，甚至只买一点点；
    - 不做止盈、不做卖出，只在「买入节奏」上做文章。

    与 v1 / v2 相比的关键修正：
    ----------------------------------------------------------------
    1. 避免"未来函数"：
        - 计算 MA250 时，仅使用「截至昨日」的历史 NAV：
          使用 history[:-1] 作为样本，今天的 NAV 只参与偏离度计算；
    2. 真正实现"高估存钱、低估用钱"：
        - 定投日（有 cash_inflow）：
            * 按估值因子调整当月 500 的实际投入；
            * 多退少补：高估少买，差额留在 cash_pool 作为"子弹"；
        - 非定投日（无 cash_inflow）：
            * 只有当 factor_raw > 1.0（明显低估）时，才允许动用 cash_pool 抄底；
            * 否则不买，让钱继续躺着等更好的机会；
    3. 统计口径更严谨：
        - principal_total：你真实打入账户的全部本金（不管是否已下场）；
        - render_summary 中同时输出：
            * 对 total_cost 的名义收益率；
            * 对 principal_total 的真实收益率。
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None) -> None:
        super().__init__(config or {})

        # 均线窗口长度：250 交易日，大致一年
        self.ma_window: int = int(self.config.get("ma_window", 250))

        # 每月基础定投金额（单位：元）
        self.base_amount: float = float(self.config.get("base_amount", 500.0))

        # 偏离度放大倍数：factor_raw = 1 - bias * multiplier
        # multiplier 越大，对高估/低估的反应越激进
        self.multiplier: float = float(self.config.get("multiplier", 2.0))

        # 为防止在高估时完全踏空，给定一个最小买入因子（仅用于"定投日"）
        # 例如：min_factor = 0.3 => 最少买 30% 的 base_amount
        self.min_factor: float = float(self.config.get("min_factor", 0.3))

    # ------------------------------------------------------------------
    # 生命周期钩子
    # ------------------------------------------------------------------
    def on_start(self) -> None:
        """
        初始化内部状态。
        """
        self.state.setdefault("nav_history", [])       # 记录每日 NAV
        self.state.setdefault("initialized", False)

    # ------------------------------------------------------------------
    # 每个交易日的策略逻辑
    # ------------------------------------------------------------------
    def on_bar(self, ctx: Context) -> Signal:
        """
        每个交易日的核心逻辑：

        - 先记录今天的 NAV（用于后续均线计算）；
        - 再基于「截至昨日」的 NAV 历史计算 MA250 和偏离度；
        - 再根据是否是"定投日"决定：
            * 定投日：对当期 inflow 做「估值加权」，多退少补；
            * 非定投日：仅在明显低估时动用 cash_pool 抄底。
        """
        nav: float = float(ctx.nav)
        date = ctx.date

        # -----------------------------
        # 1. 累积 NAV 历史
        # -----------------------------
        history: List[float] = self.state.get("nav_history") or []
        history.append(nav)
        self.state["nav_history"] = history

        # -----------------------------
        # 2. 读取资金信息
        # -----------------------------
        cash_pool: float = float(ctx.cash_pool)
        cash_inflow: float = float(getattr(ctx, "cash_inflow", 0.0) or 0.0)

        buy_cash: float = 0.0
        sell_units: float = 0.0   # 本策略不卖出
        note_parts: list[str] = []

        # -----------------------------
        # 3. 特殊处理：首日建仓
        # -----------------------------
        if not self.state.get("initialized", False):
            self.state["initialized"] = True

            # 首日一般是初始 2000 元，我们直接全仓买入作为起点
            # （如果你想让首日也走均线逻辑，这里可以改掉）
            if cash_pool > 0:
                buy_cash = cash_pool
                note_parts.append(f"首日建仓：一次性买入 {buy_cash:.2f} 元")
                return Signal(buy_cash=buy_cash, sell_units=sell_units, note="; ".join(note_parts))
            else:
                # 极端情况下首日没有钱，那就什么也不做
                note_parts.append("首日无可用资金，暂不建仓")
                return Signal(buy_cash=0.0, sell_units=0.0, note="; ".join(note_parts))

        # 如果账户里压根没钱，就不用算估值了
        if cash_pool <= 0:
            return Signal(buy_cash=0.0, sell_units=0.0, note="现金为 0，今日不买入")

        # -----------------------------
        # 4. 计算 MA250 & 偏离度（使用"截至昨日"的数据）
        # -----------------------------
        # 截至昨日的 NAV 历史
        prev_history: List[float] = history[:-1]

        # 如果昨天之前连一条数据都没有（几乎不可能），就按 no-op 处理
        if not prev_history:
            # 没有过去数据，只能当作均线=当前价，无偏离
            ma_val = nav
            bias = 0.0
        else:
            # 用截至昨日的历史数据计算 MA250
            if len(prev_history) < self.ma_window:
                window_slice = prev_history
            else:
                window_slice = prev_history[-self.ma_window:]

            ma_val = sum(window_slice) / len(window_slice)
            bias = (nav - ma_val) / ma_val if ma_val != 0 else 0.0

        # 原始因子（不加地板）——用来判断"是否严重低估/高估"
        factor_raw: float = 1.0 - bias * self.multiplier

        # -----------------------------
        # 5. 区分：定投日 vs 非定投日
        # -----------------------------
        if cash_inflow > 0:
            # -------------------------------------------------
            # 场景 A：定投日 —— 对本月定投做"估值加权"
            # -------------------------------------------------
            # 定投日不使用 factor_raw 作为最终因子，而是加一个下限，
            # 防止在高估时完全不买，导致长期踏空。
            factor_for_inflow = max(self.min_factor, factor_raw)

            # 基于估值因子的"目标买入金额"
            planned_buy = self.base_amount * factor_for_inflow

            # 实际可用资金 = 当前 cash_pool
            total_available = cash_pool

            buy_cash = min(planned_buy, total_available)

            remaining = total_available - buy_cash

            note_parts.append(
                f"[定投日] NAV={nav:.4f}, MA{self.ma_window}={ma_val:.4f}, "
                f"偏离度={bias:.2%}, 原始因子={factor_raw:.2f}, "
                f"生效因子={factor_for_inflow:.2f}；"
                f"计划买入 {planned_buy:.2f} 元，实际买入 {buy_cash:.2f} 元，"
                f"资金池剩余 {remaining:.2f} 元"
            )

        else:
            # -------------------------------------------------
            # 场景 B：非定投日 —— 只在"明显低估"时抄底
            # -------------------------------------------------
            # 规则：
            #   - 如果 factor_raw > 1.0，说明 NAV 明显低于均线（低估）
            #     => 允许用一部分 cash_pool 做额外买入；
            #   - 否则，不买，让 cash_pool 继续囤着。
            if factor_raw > 1.0:
                # 这里我们可以稍微激进一点，用 base_amount * factor_raw 作为"抄底力度"
                planned_buy = self.base_amount * factor_raw

                total_available = cash_pool
                buy_cash = min(planned_buy, total_available)
                remaining = total_available - buy_cash

                note_parts.append(
                    f"[非定投日低估抄底] NAV={nav:.4f}, MA{self.ma_window}={ma_val:.4f}, "
                    f"偏离度={bias:.2%}, 原始因子={factor_raw:.2f}；"
                    f"计划抄底 {planned_buy:.2f} 元，实际买入 {buy_cash:.2f} 元，"
                    f"资金池剩余 {remaining:.2f} 元"
                )
            else:
                buy_cash = 0.0
                note_parts.append(
                    f"[非定投日观望] NAV={nav:.4f}, MA{self.ma_window}={ma_val:.4f}, "
                    f"偏离度={bias:.2%}, 原始因子={factor_raw:.2f}；"
                    f"估值未到低估区，不动用资金池"
                )

        note = "; ".join(note_parts)
        return Signal(buy_cash=buy_cash, sell_units=sell_units, note=note)

    # ------------------------------------------------------------------
    # 回测结束后输出汇总
    # ------------------------------------------------------------------
    def get_stats(self) -> Dict[str, Any]:
        """
        返回策略专有统计信息
        """
        nav_history = self.state.get("nav_history") or []
        return {
            "ma_window": self.ma_window,
            "nav_history_len": len(nav_history),
        }

    def render_summary(self, summary: Dict[str, Any]) -> None:
        """
        由 run_backtest.py 在回测结束后调用，用于打印策略视角下的汇总信息。
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
        
        print(f"\n【交易统计】")
        print(f"   买入次数: {buy_count}")
        print(f"   卖出次数: {sell_count}")
        print(f"   总买入金额:  {total_buy_amount:>12,.2f} 元")
        print(f"   总卖出金额:  {total_sell_amount:>12,.2f} 元")
        
        # 策略专有信息
        ma_window = summary.get("ma_window", self.ma_window)
        nav_history_len = summary.get("nav_history_len", 0)
        
        print(f"\n【策略内部状态】")
        print(f"   均线窗口长度 (ma_window):         {ma_window}")
        print(f"   记录的 NAV 历史长度 (nav_history_len):  {nav_history_len}")
        
        print("\n" + "=" * 70)
        print(f"✅ 回测完成（{strategy_name}）")
        print("=" * 70)

    # ------------------------------------------------------------------
    # 元数据：供 run_backtest 打印
    # ------------------------------------------------------------------
    def get_name(self) -> str:
        return "MA250 均线增强定投策略"
