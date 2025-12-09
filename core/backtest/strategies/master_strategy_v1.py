"""
主人策略 v1 — 兴全合润专用策略（对齐《理财建议》旧脚本语义）

核心要点：
- 初始一次性买入（首日用全部现金建仓）
- 每月定投资金先进入现金池，由策略决定何时买
- 止盈条件：基于【未实现收益率 = (市值 - 成本) / 成本】
- 逢低补仓：基于【NAV 相对历史最高 NAV 的回撤】
- last_peak_nav：纯粹的历史最高净值，不随交易重置
- 本金永不卖出：止盈只卖“盈利对应份额”
- 三段式止盈：10% / 20% / 30%（在旧脚本 20% 单档的基础上扩展）
"""

from typing import Dict, Any
from .base import Strategy, Context, Signal


class MasterStrategyV1(Strategy):

    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)

        # 每月定投额度（由引擎负责注入 cash_pool，这里只做配置记录）
        self.monthly_invest: float = self.config.get("monthly_invest", 500.0)

        # 止盈档位（基于【未实现收益率】）
        # 旧脚本只有 20%，这里扩展为 10 / 20 / 30 三档
        self.tp1: float = self.config.get("tp1", 0.10)   # 未实现收益率 ≥ 10%
        self.tp2: float = self.config.get("tp2", 0.20)   # 未实现收益率 ≥ 20%
        self.tp3: float = self.config.get("tp3", 0.30)   # 未实现收益率 ≥ 30%

        # 逢低补仓档位（基于【相对历史高点】的回撤）
        self.dip1: float = self.config.get("dip1", -0.05)   # 回撤 ≥ 5%
        self.dip2: float = self.config.get("dip2", -0.10)   # 回撤 ≥ 10%
        self.dip3: float = self.config.get("dip3", -0.15)   # 回撤 ≥ 15%

        # 深度回撤时，允许动用的最大“本金”额度（每次）
        self.max_principal_per_deep_dip: float = self.config.get(
            "max_principal_per_deep_dip", 200.0
        )

    # =======================================================
    # 生命周期
    # =======================================================
    def on_start(self) -> None:
        """初始化策略状态"""
        self.state["last_peak_nav"] = None    # 历史最高 NAV
        self.state["total_cost"] = 0.0        # 总成本（和旧脚本 fund_cost 一致）
        self.state["prev_month"] = None       # 上一个月份 (year, month)
        self.state["units"] = 0.0             # 当前持仓份额
        self.state["cash_pool"] = 0.0         # 当前现金池

    def on_bar(self, ctx: Context) -> Signal:
        """每日被回测引擎调用"""
        date = ctx.date
        nav = ctx.nav

        # 初始化 last_peak_nav 为首日 NAV
        if self.state["last_peak_nav"] is None:
            self.state["last_peak_nav"] = nav

        # 同步实时仓位与现金池、成本
        units: float = ctx.portfolio.units
        cash_pool: float = ctx.cash_pool
        total_cost: float = ctx.portfolio.total_cost

        self.state["units"] = units
        self.state["cash_pool"] = cash_pool
        self.state["total_cost"] = total_cost

        buy_cash: float = 0.0
        sell_units: float = 0.0
        notes = []

        # ========== 月度信息，仅做记录 ==========
        month_key = (date.year, date.month)
        if self.state["prev_month"] is None or month_key != self.state["prev_month"]:
            self.state["prev_month"] = month_key
            cash_inflow = getattr(ctx, "cash_inflow", 0.0)
            if cash_inflow > 0:
                notes.append(
                    f"新月份 {date.year}-{date.month}：现金流入 "
                    f"{cash_inflow:.0f} 元（进入现金池，等待策略使用）"
                )

        # ========== 0. 首次建仓：用全部现金一次性买入 ==========
        if units == 0 and cash_pool > 0:
            buy_cash = cash_pool
            notes.append(f"首次建仓：使用现金池全部资金买入 {buy_cash:.0f} 元")

        # ========== 1. 止盈逻辑（基于未实现收益率），若当天没有买入 ==========
        if buy_cash == 0:
            tp_result = self._check_take_profit(nav, units, total_cost)
            if tp_result["sell_units"] > 0:
                sell_units = tp_result["sell_units"]
                notes.append(tp_result["note"])

        # ========== 2. 逢低补仓逻辑（基于历史高点回撤），若当天无买入且未止盈 ==========
        if buy_cash == 0 and sell_units == 0:
            dip_result = self._check_dip_buy(nav, units, total_cost, cash_pool)
            if dip_result["buy_cash"] > 0:
                buy_cash = dip_result["buy_cash"]
                notes.append(dip_result["note"])

        # ========== 3. 更新历史最高 NAV（完全对齐旧脚本逻辑） ==========
        if nav > self.state["last_peak_nav"]:
            self.state["last_peak_nav"] = nav

        return Signal(
            buy_cash=buy_cash,
            sell_units=sell_units,
            note="; ".join(notes) if notes else "",
        )

    # =======================================================
    # 止盈逻辑（三段式，基于“未实现收益率”，且不卖本金）
    # =======================================================
    def _check_take_profit(
        self, nav: float, units: float, total_cost: float
    ) -> Dict[str, Any]:
        """
        对齐旧脚本的语义：
        - 旧脚本的止盈条件：unrealized_gain_pct = (市值 - 成本) / 成本 >= tp_threshold
        - 这里扩展为三档：tp1 / tp2 / tp3（默认 10% / 20% / 30%）
        - 但卖出时遵守“本金永不卖出”：只卖盈利对应份额，不动本金
        """
        result = {"sell_units": 0.0, "note": ""}

        if units <= 0 or total_cost <= 0:
            return result

        # 当前市值 & 未实现盈利
        fund_value = units * nav
        profit = fund_value - total_cost
        if profit <= 0:
            return result

        # 未实现收益率（对齐旧脚本）
        unrealized_gain_pct = profit / total_cost

        # 未达到第一级止盈阈值
        if unrealized_gain_pct < self.tp1:
            return result

        # 盈利对应的份额上限（本金永不卖出）
        profit_units = profit / nav

        # 确定当前档位
        if self.tp1 <= unrealized_gain_pct < self.tp2:
            tier = "10%"
            target_ratio = 0.25  # 目标：相当于 25% 持仓的盈利
        elif self.tp2 <= unrealized_gain_pct < self.tp3:
            tier = "20%"
            target_ratio = 0.25  # 再卖 25%
        else:
            tier = "30%"
            target_ratio = 1.0   # 30% 档：目标卖出全部盈利

        if tier in ("10%", "20%"):
            target_units_by_ratio = units * target_ratio
            sell_units = min(target_units_by_ratio, profit_units)
            if sell_units <= 0:
                return result
            result["sell_units"] = sell_units
            result["note"] = (
                f"止盈{tier}：未实现收益率 {unrealized_gain_pct*100:.2f}%，"
                f"卖出盈利对应份额 {sell_units:.2f} 份（不动本金）"
            )
        else:
            # 30% 档：直接卖掉全部盈利对应份额
            sell_units = profit_units
            if sell_units <= 0:
                return result
            result["sell_units"] = sell_units
            result["note"] = (
                f"止盈30%：未实现收益率 {unrealized_gain_pct*100:.2f}% ，"
                f"卖出全部盈利对应份额 {sell_units:.2f} 份（不动本金）"
            )

        return result

    # =======================================================
    # 低估补仓逻辑（对齐旧脚本：基于历史高点回撤）
    # =======================================================
    def _check_dip_buy(
        self,
        nav: float,
        units: float,
        total_cost: float,
        cash_pool: float,
    ) -> Dict[str, Any]:
        """
        对齐旧脚本的逻辑：

        drawdown = (nav - last_peak_nav) / last_peak_nav  （负数为回撤）

        新版扩展为三档：
        -5% ~ -10%      使用盈利的 40% 补仓
        -10% ~ -15%     使用盈利的 70% 补仓
        ≤ -15%          使用盈利 100% + 本金最多 200 元补仓

        最终 buy_cash 不得超过 cash_pool（现金池）。
        """
        result = {"buy_cash": 0.0, "note": ""}

        last_peak_nav = self.state["last_peak_nav"]
        if last_peak_nav is None or last_peak_nav <= 0:
            return result

        # 相对历史最高净值的回撤（负数）
        drawdown = (nav - last_peak_nav) / last_peak_nav

        # 小回撤不补仓（> -5%）
        if drawdown > self.dip1:
            return result

        if units <= 0 or total_cost <= 0:
            return result

        # 当前市值 & 未实现盈利
        fund_value = units * nav
        profit = max(0.0, fund_value - total_cost)

        buy_amount = 0.0
        note = ""

        # -5% ~ -10%：用盈利 40% 补仓
        if self.dip2 < drawdown <= self.dip1:
            if profit <= 0:
                return result
            buy_amount = profit * 0.40
            note = (
                f"回撤约 5%：当前回撤 {drawdown*100:.2f}% ，"
                "使用盈利 40% 补仓"
            )

        # -10% ~ -15%：用盈利 70% 补仓
        elif self.dip3 < drawdown <= self.dip2:
            if profit <= 0:
                return result
            buy_amount = profit * 0.70
            note = (
                f"回撤约 10%：当前回撤 {drawdown*100:.2f}% ，"
                "使用盈利 70% 补仓"
            )

        # ≤ -15%：盈利 100% + 本金最多 200 元
        else:  # drawdown <= self.dip3
            profit_part = profit if profit > 0 else 0.0
            principal_part = min(self.max_principal_per_deep_dip, cash_pool)
            buy_amount = profit_part + principal_part
            if buy_amount <= 0:
                return result
            note = (
                f"回撤 ≥ 15%：当前回撤 {drawdown*100:.2f}% ，"
                f"使用盈利 100% + 本金 {principal_part:.0f} 元 补仓"
            )

        # 不得超过现金池
        buy_amount = min(buy_amount, cash_pool)
        if buy_amount <= 0:
            return result

        result["buy_cash"] = buy_amount
        result["note"] = f"{note}（共 {buy_amount:.0f} 元）"
        return result

    # =======================================================
    # 统计 & 名称
    # =======================================================
    def get_stats(self) -> Dict[str, Any]:
        return {
            "peak_nav": self.state.get("last_peak_nav", 0.0),
            "total_cost": self.state.get("total_cost", 0.0),
        }

    def get_name(self) -> str:
        return "主人策略V1"
