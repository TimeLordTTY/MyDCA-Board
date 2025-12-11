from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional, List

from .base import Strategy, Context, Signal


@dataclass
class ProfitRecycleConfig:
    """
    利润回收策略 v8 配置项
    """
    # 预投入本金比例（每次有现金流入时，按比例划入"预投入体系"）
    pre_invest_ratio: float = 0.10  # 例如 0.10 = 每笔现金流入的 10%

    # 深跌阈值：相对历史高点的回撤百分比（负数）
    # 例如 -0.20 表示从高点回撤 20% 触发深跌补仓
    deep_dip_threshold: float = -0.20

    # 深跌触发时，动用当前预投入锁定余额的比例
    deep_dip_use_ratio: float = 0.50  # 例如 0.50 表示用掉当前锁定预投入的一半


class ProfitRecycleStrategy(Strategy):
    """
    利润回收策略 v8 — 深跌小额补仓版

    设计目标：
    - 在纯定投基线的基础上，抽取一部分"预投入本金"形成子弹，在深跌时集中打出去。
    - 永远在场：正常情况下，所有"未锁定现金"立刻买入，避免长期踏空。
    - 避免"隐形作弊"：收益率口径全部以【全部本金】为基准，不因囤钱美化收益。

    资金流转逻辑（简化版）：
    1. 每次有现金流入（定投日）：
       - 增加 principal_total（真实打入本金）
       - 按 pre_invest_ratio 抽取一部分记入 pre_invest_locked（预投入锁定）
       - 剩余现金作为"可自由使用现金"（正常定投）

    2. 日度买入逻辑：
       - 任何时刻，locked_cash = min(pre_invest_locked, cash_pool)
       - 可用于【普通买入】的现金 = cash_pool - locked_cash
       - 基线行为：把这部分"未锁定现金"全部买入（模拟纯定投效果）

    3. 深跌补仓逻辑：
       - 使用 last_peak_nav 记录历史最高净值
       - 计算相对高点回撤 drawdown = (nav - last_peak_nav) / last_peak_nav
       - 当 drawdown <= deep_dip_threshold（例如 -20%），且本轮下跌尚未触发过深跌补仓：
         * 从 pre_invest_locked 中"释放"一部分：deep_dip_use_ratio（例如 50%）
         * 实质操作：
              pre_invest_locked -= deep_dip_amount
              pre_invest_released += deep_dip_amount
           现金实际仍然在 cash_pool，只是从"锁定"变为"可自由买入"
         * 然后按照步骤 2 的规则，统一用"未锁定现金"买入

    4. 峰值重置逻辑：
       - 当 nav 创历史新高时：
         * 更新 last_peak_nav = nav
         * 重置 deep_dip_triggered = False（下一轮下跌可以再次触发深跌补仓）
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None) -> None:
        super().__init__(config or {})
        self.cfg = ProfitRecycleConfig(
            pre_invest_ratio=self.config.get("pre_invest_ratio", 0.10),
            deep_dip_threshold=self.config.get("deep_dip_threshold", -0.20),
            deep_dip_use_ratio=self.config.get("deep_dip_use_ratio", 0.50),
        )

    # ------------------------------------------------------------------
    # 生命周期：策略启动
    # ------------------------------------------------------------------
    def on_start(self) -> None:
        """
        初始化内部状态
        """
        state = self.state
        # 资金统计
        state["pre_invest_locked"] = 0.0        # 当前仍处于"锁定状态"的预投入现金（子弹）
        state["pre_invest_total_in"] = 0.0      # 历史累计划入预投入体系的金额总和
        state["pre_invest_released"] = 0.0      # 历史上从锁定池中释放出来用于深跌补仓的金额总和
        state["redeem_profit_pool"] = 0.0       # 预留字段（目前版本不做止盈赎回）
        state["redeem_principal_pool"] = 0.0    # 预留字段（目前版本不做本金回收）

        # 行情相关
        state["last_peak_nav"] = 0.0            # 历史最高净值
        state["deep_dip_triggered"] = False     # 当前这轮下跌中是否已经触发过一次深跌补仓
        state["deep_dip_count"] = 0            # 全回测周期内，深跌补仓触发次数

    # ------------------------------------------------------------------
    # 生命周期：逐日回测
    # ------------------------------------------------------------------
    def on_bar(self, ctx: Context) -> Signal:
        """
        每个交易日的决策逻辑
        """
        nav: float = ctx.nav
        date = getattr(ctx, "date", None)

        cash_pool: float = ctx.cash_pool
        cash_inflow: float = getattr(ctx, "cash_inflow", 0.0) or 0.0

        state = self.state
        note_parts: List[str] = []

        # 1. 处理预投入本金：从当日现金流中抽取一部分记入锁定池
        reserve: float = 0.0
        if cash_inflow > 0 and self.cfg.pre_invest_ratio > 0:
            reserve = cash_inflow * self.cfg.pre_invest_ratio
            state["pre_invest_locked"] += reserve
            state["pre_invest_total_in"] += reserve
            note_parts.append(
                f"预投入划入: {reserve:.2f} 元 -> 锁定池 (pre_invest_locked={state['pre_invest_locked']:.2f})"
            )

        # 2. 更新历史高点 & 回撤，决定是否触发深跌补仓
        last_peak_nav: float = state.get("last_peak_nav", 0.0)
        deep_dip_triggered: bool = state.get("deep_dip_triggered", False)

        # 初始化高点
        if last_peak_nav <= 0.0:
            last_peak_nav = nav
            state["last_peak_nav"] = nav
            drawdown = 0.0
        else:
            # 新高则重置高点和深跌标记
            if nav > last_peak_nav:
                last_peak_nav = nav
                state["last_peak_nav"] = nav
                state["deep_dip_triggered"] = False
                drawdown = 0.0
                note_parts.append(f"净值创新高: nav={nav:.4f}, last_peak_nav={last_peak_nav:.4f}")
            else:
                drawdown = (nav - last_peak_nav) / last_peak_nav

        # 3. 深跌补仓：只在满足阈值、且本轮尚未触发过的情况下，释放预投入锁定池的一部分
        deep_dip_info = ""
        if (
            last_peak_nav > 0.0
            and drawdown <= self.cfg.deep_dip_threshold
            and not state.get("deep_dip_triggered", False)
            and state["pre_invest_locked"] > 0.0
        ):
            # 理论可用额度 = 当前锁定池 * 使用比例
            want_use = state["pre_invest_locked"] * self.cfg.deep_dip_use_ratio

            # 实际可用现金不能超过当前 cash_pool
            deep_dip_amount = min(want_use, state["pre_invest_locked"], cash_pool)

            if deep_dip_amount > 0:
                # 从锁定池中"释放"出来（解除锁定，把它变成普通现金）
                state["pre_invest_locked"] -= deep_dip_amount
                state["pre_invest_released"] += deep_dip_amount
                state["deep_dip_triggered"] = True
                state["deep_dip_count"] = state.get("deep_dip_count", 0) + 1

                deep_dip_info = (
                    f"深跌补仓触发: 回撤={drawdown:.2%} ≤ {self.cfg.deep_dip_threshold:.2%}, "
                    f"释放锁定资金 {deep_dip_amount:.2f} 元 -> 转为普通可用现金"
                )
                note_parts.append(deep_dip_info)

        # 4. 计算普通买入可用的现金（锁定池不允许被普通买入动用）
        #    locked_cash 表示当前 cash_pool 中，有多少需要被视为"仍然锁定"的预投入资金
        locked_cash = min(state["pre_invest_locked"], cash_pool)
        normal_available_cash = max(0.0, cash_pool - locked_cash)

        buy_cash = 0.0
        sell_units = 0.0

        # 5. 基线行为：所有未锁定的现金，全部用于买入（模拟纯定投效果）
        if normal_available_cash > 0:
            buy_cash = normal_available_cash
            note_parts.append(
                f"常规买入: 使用未锁定现金 {buy_cash:.2f} 元 (cash_pool={cash_pool:.2f}, locked={locked_cash:.2f})"
            )

        # 本版本不做止盈卖出（sell_units 始终为 0）
        return Signal(
            buy_cash=buy_cash,
            sell_units=sell_units,
            note="; ".join(note_parts),
        )

    # ------------------------------------------------------------------
    # 策略名称
    # ------------------------------------------------------------------
    def get_name(self) -> str:
        return "利润回收策略 v8 — 深跌小额补仓版"

    # ------------------------------------------------------------------
    # 策略统计信息
    # ------------------------------------------------------------------
    def get_stats(self) -> Dict[str, Any]:
        """
        返回策略专有统计信息
        """
        return {
            "pre_invest_locked": self.state.get("pre_invest_locked", 0.0),
            "pre_invest_total_in": self.state.get("pre_invest_total_in", 0.0),
            "pre_invest_released": self.state.get("pre_invest_released", 0.0),
            "deep_dip_count": self.state.get("deep_dip_count", 0),
            "last_peak_nav": self.state.get("last_peak_nav", 0.0),
        }

    # ------------------------------------------------------------------
    # 回测结果汇总打印
    # ------------------------------------------------------------------
    def render_summary(self, summary: Dict[str, Any]) -> None:
        """
        对回测引擎传入的 summary 做结果展示 + 真·收益率校准
        """
        print()
        print("=" * 70)
        print("                         📊 回测结果（由策略输出）")
        print("=" * 70)
        
        # 基础信息
        strategy_name = summary.get("strategy_name", self.get_name())
        fund_code = summary.get("fund_code", "未知")
        start_date = summary.get("start_date")
        end_date = summary.get("end_date")
        days = summary.get("days", 0)
        
        print()
        print("【基础信息】")
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
        
        print()
        print("【资金情况】")
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
        
        print()
        print("【收益情况】")
        print(f"   名义盈亏金额(对下场资金):  {nominal_pnl:>+12,.2f} 元")
        print(f"   名义总收益率(对下场资金):  {nominal_return * 100:>12.2f}%")
        print(f"   真实总收益率(对全部本金):  {real_return * 100:>12.2f}%")
        print(f"   年化收益率:                {annual_return * 100:>12.2f}%")
        
        # 交易统计
        buy_count = summary.get("buy_count", 0)
        sell_count = summary.get("sell_count", 0)
        total_buy_amount = summary.get("total_buy_amount", 0.0)
        total_sell_amount = summary.get("total_sell_amount", 0.0)
        
        print()
        print("【交易统计】")
        print(f"   买入次数: {buy_count}")
        print(f"   卖出次数: {sell_count}")
        print(f"   总买入金额:  {total_buy_amount:>12,.2f} 元")
        print(f"   总卖出金额:  {total_sell_amount:>12,.2f} 元")
        
        # 策略专有信息 - 内部资金池状态
        pre_invest_locked = summary.get("pre_invest_locked", 0.0)
        pre_invest_total_in = summary.get("pre_invest_total_in", 0.0)
        pre_invest_released = summary.get("pre_invest_released", 0.0)
        deep_dip_count = summary.get("deep_dip_count", 0)
        last_peak_nav = summary.get("last_peak_nav", 0.0)
        
        print()
        print("【内部资金池状态】")
        print(f"   预投入锁定余额 (pre_invest_locked):          {pre_invest_locked:>12,.2f} 元")
        print(f"   预投入历史累计划入 (pre_invest_total_in):     {pre_invest_total_in:>12,.2f} 元")
        print(f"   深跌已释放总额 (pre_invest_released):        {pre_invest_released:>12,.2f} 元")
        print(f"   深跌补仓触发次数 (deep_dip_count):                 {deep_dip_count:>6d} 次")
        print(f"   观测最高净值 (last_peak_nav):                     {last_peak_nav:>8.4f}")
        
        # 校准视角
        print()
        print("【校准视角】")
        print(f"   实际买入成本 total_cost:              {total_cost:>12,.2f} 元")
        print(f"   真实打入本金 principal_total:         {principal_total:>12,.2f} 元")
        print(f"   期末总资产(基金市值+现金):            {final_assets:>12,.2f} 元")
        print(f"   ▶ 名义收益率(对下场资金):                {nominal_return * 100:>8.2f}%")
        print(f"   ▶ 真实收益率(对全部本金):                {real_return * 100:>8.2f}%")
        
        print()
        print("=" * 70)
        print(f"✅ 回测完成（{strategy_name}）")
        print("=" * 70)

    # =======================================================
    # 档位信息（当前版本无止盈/无补仓，仅说明用途）
    # =======================================================
    def get_levels_info(self) -> Dict[str, Any]:
        """
        返回策略的"档位"说明，供 run_backtest 做展示。
        当前 v8 版本：预投入体系 + 深跌补仓
        """
        return {
            "mode": "profit_recycle_v8",
            "tp_levels_desc": [
                "当前版本不做止盈卖出"
            ],
            "dip_levels_desc": [
                f"深跌阈值: 回撤 ≥ {abs(self.cfg.deep_dip_threshold):.0%}",
                f"深跌触发时释放预投入锁定池的 {self.cfg.deep_dip_use_ratio:.0%}",
                f"预投入比例: 每笔现金流入的 {self.cfg.pre_invest_ratio:.0%} 划入锁定池"
            ],
            "description": "v8 深跌小额补仓版：抽取部分本金形成子弹，深跌时集中释放。",
        }
