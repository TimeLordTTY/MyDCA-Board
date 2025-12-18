# -*- coding: utf-8 -*-
"""
纯基础定投策略（基准线）

- 初始资金：只要进了账户，就全额买入
- 每月定投：资金打入后，只要在账户里，就全额买入
- 任何形式的现金（分红、利息等）都不长期躺在现金池里
- 不做止盈、不做补仓、不择时：只买不卖，长期持有
"""

from typing import Any, Dict, Optional, List

from .base import Strategy, Context, Signal
from .registry import register_strategy


@register_strategy("pure_sip", version="v1", set_as_default=True)
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

    strategy_key = "pure_sip"
    strategy_version = "v1"
    display_name = "纯基础定投策略"

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
        - 不区分"初始资金 / 每月定投 / 分红"：**只要 cash > 0，就全额买入**
        - 这样可以自然实现：
            * 首日全额建仓
            * 每月定投资金到账后当日即买入
            * 分红现金自动再投资（避免现金滞留）
        """
        cash = ctx.cash  # 使用统一的 cash 字段
        cash_inflow = getattr(ctx, "cash_inflow", 0.0) or 0.0

        buy_cash = 0.0
        sell_units = 0.0
        note_parts: List[str] = []

        # 判断是否应该买入
        should_buy = False
        if self.invest_all_cash and cash > 0:
            # 检查最小投入金额
            if cash >= self.min_invest_amount:
                should_buy = True

        if should_buy:
            buy_cash = cash
            self.state["total_invest_count"] = self.state.get("total_invest_count", 0) + 1

            # 日志仅用于区分场景，方便你在 CSV 里看
            if not self.state["initialized"]:
                self.state["initialized"] = True
                note_parts.append(f"首日建仓: 全额买入 {buy_cash:.2f} 元")
            elif cash_inflow and cash_inflow > 0:
                note_parts.append(f"月度定投: 全额买入 {buy_cash:.2f} 元")
            else:
                # 无 inflow，但 cash > 0，多半是分红 / 零头
                if self.reinvest_dividend:
                    note_parts.append(f"红利/余额再投: 全额买入 {buy_cash:.2f} 元")
                else:
                    buy_cash = 0.0
                    note_parts.append(f"红利/余额: 不再投资，保留 {cash:.2f} 元")

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

