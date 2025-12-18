"""
利润回收策略 (Profit Recycle Strategy) — v11
------------------------------------------

设计目标：
- 在不改动回测引擎/框架的前提下，尽量在“长期慢牛/震荡”的环境里，通过
  ① 高位少量“收割波动”形成弹药
  ② 低位分级释放弹药加仓
  来争取跑赢纯定投。

关键点（实事求是的约束）：
- 这是“用择时换超额”的策略：如果行情单边长牛且回撤很浅，策略可能不如纯定投。
- 依然不引入杠杆，不做短线频繁交易；卖出仅在“高估区”小比例发生，目的是形成弹药池，
  而不是追求择时完美。

本策略仅改策略文件，不改引擎：所以“现金收益（货基/逆回购）”不在这里虚拟生成，避免
产生“凭空多出来的钱”的不真实结果（如需模拟现金收益，应在引擎层实现）。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from .base import Strategy, Context, Signal
from .registry import register_strategy


@dataclass
class DeepDipLevel:
    """深跌档位：回撤阈值（负数）+ 释放比例（0~1）"""
    threshold: float  # e.g. -0.10 表示回撤>=10%
    use_ratio: float  # e.g. 0.5 表示释放锁定池 50%


@register_strategy("profit_recycle", version="v11", set_as_default=True)
class ProfitRecycleStrategy(Strategy):
    """
    利润回收策略 v11 — 动态预投入 + 分级深跌补仓 + 高位小比例收割波动版

    组件：
    1) 动态预投入（形成锁定池 / 弹药池）
       - 低估区（nav < MA）：锁定比例 0%（尽量全仓在场）
       - 中性区：锁定比例 5%
       - 高估区（nav > MA*(1+high_bias)）：锁定比例 20%

    2) 深跌分级释放弹药
       - 档位1：回撤≥10% 释放 50%
       - 档位2：回撤≥15% 释放 100%

    3) 高估区少量卖出（收割波动，形成弹药）
       - 当 nav 偏离 MA 达到 take_profit_bias，且接近阶段新高时，
         卖出一定比例仓位，把卖出所得"计入锁定池"，等待下次深跌释放。
    """
    
    # 策略元信息（用于注册表）
    strategy_key = "profit_recycle"
    strategy_version = "v11"
    display_name = "利润回收策略 v11"

    def __init__(self, config: Dict[str, Any] | None = None):
        super().__init__(config)

        # ========= 均线/估值分区 =========
        self.ma_window: int = int(self.config.get("ma_window", 250))
        self.high_bias: float = float(self.config.get("high_bias", 0.20))  # 高估区：nav > MA*(1+high_bias)

        # ========= 动态预投入（锁定池） =========
        self.lock_ratio_low: float = float(self.config.get("lock_ratio_low", 0.00))
        self.lock_ratio_mid: float = float(self.config.get("lock_ratio_mid", 0.05))
        self.lock_ratio_high: float = float(self.config.get("lock_ratio_high", 0.20))

        # ========= 深跌分级释放 =========
        levels_cfg = self.config.get("deep_dip_levels", None)
        if levels_cfg:
            self.deep_dip_levels: List[DeepDipLevel] = [
                DeepDipLevel(float(x["threshold"]), float(x["use_ratio"])) for x in levels_cfg
            ]
        else:
            self.deep_dip_levels = [
                DeepDipLevel(-0.10, 0.50),
                DeepDipLevel(-0.15, 1.00),
            ]
        # 保证从轻到重排序（threshold 例如 -0.10, -0.15）
        self.deep_dip_levels.sort(key=lambda x: x.threshold, reverse=True)

        # 是否允许“连续深跌连发”（更激进、也更像你想要的“狙击手连发”）
        self.allow_multi_deep_dip: bool = bool(self.config.get("allow_multi_deep_dip", True))
        self.rebound_reset_rate: float = float(self.config.get("rebound_reset_rate", 0.05))  # 反弹 5% 可重置
        self.debounce_days: int = int(self.config.get("debounce_days", 30))  # 冷却 N 天可重置

        # ========= 高估区小比例卖出（收割波动形成弹药） =========
        self.take_profit_enabled: bool = bool(self.config.get("take_profit_enabled", True))
        self.take_profit_bias: float = float(self.config.get("take_profit_bias", 0.18))  # nav > MA*(1+0.18)
        self.take_profit_sell_ratio: float = float(self.config.get("take_profit_sell_ratio", 0.05))  # 卖 5% 仓位
        self.take_profit_cooldown_days: int = int(self.config.get("take_profit_cooldown_days", 60))
        # 接近新高的判定：nav >= last_peak_nav * near_peak_ratio
        self.near_peak_ratio: float = float(self.config.get("near_peak_ratio", 0.98))

        # ========= 内部状态 key =========
        self.K_NAV_HISTORY = "nav_history"
        self.K_LAST_PEAK_NAV = "last_peak_nav"
        self.K_PRE_INVEST_LOCKED = "pre_invest_locked"
        self.K_PRE_INVEST_TOTAL_IN = "pre_invest_total_in"
        self.K_PRE_INVEST_RELEASED = "pre_invest_released"
        self.K_DEEP_DIP_COUNT = "deep_dip_count"

        self.K_DEEP_DIP_TRIGGERED = "deep_dip_triggered"
        self.K_LAST_DIP_DATE = "last_deep_dip_date"
        self.K_LAST_DIP_NAV = "last_deep_dip_nav"

        self.K_LAST_TP_DATE = "last_take_profit_date"
        self.K_TP_COUNT = "take_profit_count"
        self.K_TP_TOTAL_SELL_UNITS = "take_profit_sell_units_total"

        self.K_LAST_LOCK_RATIO = "last_lock_ratio"
        self.K_LAST_NAV_BIAS = "last_nav_bias"

    # ----------------- 元信息 -----------------
    @staticmethod
    def get_type() -> str:
        return "profit_recycle"

    @staticmethod
    def get_name() -> str:
        return "利润回收策略 v11 — 动态预投入 + 分级深跌补仓 + 高位收割波动版"

    @staticmethod
    def get_version() -> str:
        return "v11"

    @staticmethod
    def get_description() -> str:
        return "动态锁定资金形成弹药；深跌分级释放；高估区小比例卖出收割波动以对抗资金拖累。"

    # ----------------- 核心逻辑 -----------------
    def on_bar(self, ctx: Context) -> Signal:
        nav = float(ctx.nav)
        date = ctx.date
        cash_inflow = float(ctx.cash_inflow or 0.0)
        cash = float(ctx.cash or 0.0)  # 使用统一的 cash 字段
        units = float(ctx.portfolio.shares)

        # === state init ===
        state = ctx.state
        nav_hist: List[float] = state.get(self.K_NAV_HISTORY, [])
        last_peak_nav: float = float(state.get(self.K_LAST_PEAK_NAV, nav))
        pre_locked: float = float(state.get(self.K_PRE_INVEST_LOCKED, 0.0))

        # === 更新 NAV 历史 / MA ===
        nav_hist.append(nav)
        if len(nav_hist) > self.ma_window * 3:
            nav_hist = nav_hist[-self.ma_window * 3 :]
        state[self.K_NAV_HISTORY] = nav_hist

        ma = None
        if len(nav_hist) >= self.ma_window:
            ma = sum(nav_hist[-self.ma_window :]) / self.ma_window

        # === 更新峰值（用于回撤）===
        if nav > last_peak_nav:
            last_peak_nav = nav
            state[self.K_LAST_PEAK_NAV] = last_peak_nav
            # 创新高可重置一次“深跌触发”标记（但我们更偏好 allow_multi_deep_dip 的连发机制）
            state[self.K_DEEP_DIP_TRIGGERED] = False

        # === 估值偏离 ===
        nav_bias = 0.0
        if ma and ma > 1e-12:
            nav_bias = (nav - ma) / ma
        state[self.K_LAST_NAV_BIAS] = nav_bias

        # === 动态锁定比例 ===
        lock_ratio = self.lock_ratio_mid
        if ma is not None:
            if nav < ma:
                lock_ratio = self.lock_ratio_low
            elif nav > ma * (1.0 + self.high_bias):
                lock_ratio = self.lock_ratio_high
            else:
                lock_ratio = self.lock_ratio_mid
        state[self.K_LAST_LOCK_RATIO] = lock_ratio

        # === 把本次现金流入的一部分"计入锁定池"（注意：现金统一来自 portfolio.cash，本策略用 pre_locked 记账来限制当期可买金额）===
        if cash_inflow > 0:
            lock_amount = cash_inflow * lock_ratio
            pre_locked += lock_amount
            state[self.K_PRE_INVEST_LOCKED] = pre_locked
            state[self.K_PRE_INVEST_TOTAL_IN] = float(state.get(self.K_PRE_INVEST_TOTAL_IN, 0.0)) + lock_amount

        # === 计算回撤 ===
        drawdown = 0.0
        if last_peak_nav > 1e-12:
            drawdown = (nav - last_peak_nav) / last_peak_nav  # 负数表示回撤
        # drawdown <= -0.10 => 回撤>=10%

        # === 深跌触发 & 释放 ===
        deep_dip_triggered = bool(state.get(self.K_DEEP_DIP_TRIGGERED, False))
        last_dip_date = state.get(self.K_LAST_DIP_DATE, None)
        last_dip_nav = float(state.get(self.K_LAST_DIP_NAV, 0.0) or 0.0)

        # 允许连发：反弹一定比例 or 冷却一定天数
        if deep_dip_triggered and self.allow_multi_deep_dip:
            can_reset = False
            if last_dip_nav > 1e-12 and nav >= last_dip_nav * (1.0 + self.rebound_reset_rate):
                can_reset = True
            if last_dip_date is not None:
                try:
                    days = (date - last_dip_date).days
                    if days >= self.debounce_days:
                        can_reset = True
                except Exception:
                    pass
            if can_reset:
                deep_dip_triggered = False
                state[self.K_DEEP_DIP_TRIGGERED] = False

        # 深跌释放金额（本次额外买入）
        deep_dip_buy_cash = 0.0
        if not deep_dip_triggered:
            # 找到满足的最重档位（例如 -15% 优先于 -10%）
            matched: Optional[DeepDipLevel] = None
            for lvl in sorted(self.deep_dip_levels, key=lambda x: x.threshold):
                if drawdown <= lvl.threshold:
                    matched = lvl  # 更负的 threshold 更“深”
            if matched is not None and pre_locked > 1e-6:
                use_amount = pre_locked * matched.use_ratio
                use_amount = max(0.0, min(use_amount, pre_locked))
                pre_locked -= use_amount
                deep_dip_buy_cash = use_amount

                state[self.K_PRE_INVEST_LOCKED] = pre_locked
                state[self.K_PRE_INVEST_RELEASED] = float(state.get(self.K_PRE_INVEST_RELEASED, 0.0)) + use_amount
                state[self.K_DEEP_DIP_COUNT] = int(state.get(self.K_DEEP_DIP_COUNT, 0)) + 1

                state[self.K_DEEP_DIP_TRIGGERED] = True
                state[self.K_LAST_DIP_DATE] = date
                state[self.K_LAST_DIP_NAV] = nav

        # === 高估区小比例卖出（形成弹药池，尽量对抗资金拖累）===
        sell_units = 0.0
        if self.take_profit_enabled and units > 1e-12 and ma is not None:
            # 条件：偏离 MA 足够高 + 接近新高 + 冷却时间到
            tp_ok = nav_bias >= self.take_profit_bias and nav >= last_peak_nav * self.near_peak_ratio

            # 冷却
            if tp_ok:
                last_tp_date = state.get(self.K_LAST_TP_DATE, None)
                if last_tp_date is not None:
                    try:
                        if (date - last_tp_date).days < self.take_profit_cooldown_days:
                            tp_ok = False
                    except Exception:
                        tp_ok = False

            if tp_ok:
                sell_units = units * self.take_profit_sell_ratio
                if sell_units > 1e-12:
                    # 预估卖出所得，直接计入锁定池（弹药）
                    est_proceeds = sell_units * nav
                    pre_locked += est_proceeds
                    state[self.K_PRE_INVEST_LOCKED] = pre_locked

                    state[self.K_LAST_TP_DATE] = date
                    state[self.K_TP_COUNT] = int(state.get(self.K_TP_COUNT, 0)) + 1
                    state[self.K_TP_TOTAL_SELL_UNITS] = float(state.get(self.K_TP_TOTAL_SELL_UNITS, 0.0)) + sell_units

        # === 可用现金：cash - locked（锁定池越大，可买越少）===
        # 注意：现金统一来自 portfolio.cash，本策略用"locked 记账"限制买入现金。
        locked_cash = max(0.0, min(pre_locked, cash))
        normal_available_cash = max(0.0, cash - locked_cash)

        # 如果当日触发了卖出（高估收割），就不做买入，避免同日来回摩擦
        buy_cash = 0.0
        if sell_units <= 1e-12:
            buy_cash = normal_available_cash + deep_dip_buy_cash
            buy_cash = max(0.0, min(buy_cash, cash))

        return Signal(buy_cash=buy_cash, sell_units=sell_units)

    # ----------------- 汇总输出 -----------------
    def render_summary(self, summary: Dict[str, Any]) -> str:
        # 在 summary 中补充策略内部状态（引擎会把 ctx.state 带出来）
        s = []
        s.append("【策略配置参数】")
        s.append("   深跌模式: 多级阈值模式")
        for i, lvl in enumerate(self.deep_dip_levels, 1):
            s.append(f"      档位{i}: 回撤 ≤ {lvl.threshold*100:.0f}% 释放 {lvl.use_ratio*100:.0f}%")
        s.append("   预投入模式: 动态预投入（按 MA{} + 高低估分区自动调整）".format(self.ma_window))
        s.append(f"      低位锁定: {self.lock_ratio_low*100:.0f}%")
        s.append(f"      中性锁定: {self.lock_ratio_mid*100:.0f}%")
        s.append(f"      高位锁定: {self.lock_ratio_high*100:.0f}%")
        s.append(f"   允许连续深跌 (allow_multi_deep_dip):   {self.allow_multi_deep_dip}")
        s.append(f"   反弹重置率 (rebound_reset_rate):       {self.rebound_reset_rate*100:.2f}%")
        s.append(f"   冷却天数 (debounce_days):              {self.debounce_days} 天")
        s.append(f"   高估收割开关 (take_profit_enabled):    {self.take_profit_enabled}")
        if self.take_profit_enabled:
            s.append(f"      触发偏离 (take_profit_bias):         {self.take_profit_bias*100:.2f}%")
            s.append(f"      卖出比例 (sell_ratio):               {self.take_profit_sell_ratio*100:.2f}%")
            s.append(f"      冷却天数 (cooldown):                 {self.take_profit_cooldown_days} 天")
            s.append(f"      接近新高阈值 (near_peak_ratio):      {self.near_peak_ratio*100:.1f}%")

        # state-based info
        state = summary.get("state", {}) or {}
        s.append("")
        s.append("【内部资金池状态】")
        s.append(f"   预投入锁定余额:                     {float(state.get(self.K_PRE_INVEST_LOCKED, 0.0)):.2f} 元")
        s.append(f"   预投入历史累计划入:              {float(state.get(self.K_PRE_INVEST_TOTAL_IN, 0.0)):.2f} 元")
        s.append(f"   深跌已释放总额:                  {float(state.get(self.K_PRE_INVEST_RELEASED, 0.0)):.2f} 元")
        s.append(f"   深跌补仓触发次数:                      {int(state.get(self.K_DEEP_DIP_COUNT, 0))} 次")
        s.append(f"   高估收割次数:                          {int(state.get(self.K_TP_COUNT, 0))} 次")
        s.append(f"   高估累计卖出份额:                  {float(state.get(self.K_TP_TOTAL_SELL_UNITS, 0.0)):.6f} 份")
        s.append(f"   观测最高净值:                       {float(state.get(self.K_LAST_PEAK_NAV, 0.0)):.4f}")
        s.append(f"   NAV 历史长度:                       {int(state.get(self.K_NAV_HISTORY, []) and len(state.get(self.K_NAV_HISTORY)) or 0)}")
        s.append(f"   最后生效预投入比例:                 {float(state.get(self.K_LAST_LOCK_RATIO, 0.0))*100:.2f}%")
        s.append(f"   最后估值偏离度:                     {float(state.get(self.K_LAST_NAV_BIAS, 0.0))*100:.2f}%")

        return "\n".join(s)
