from __future__ import annotations

from datetime import datetime
from typing import Any, Dict, Optional, List

from .base import Strategy, Context, Signal
from .registry import register_strategy


@register_strategy("profit_recycle", version="v10")
class ProfitRecycleStrategyV10(Strategy):
    """
    利润回收策略 v10 — 动态预投入 + 分级深跌补仓版

    设计目标：
    - 在纯定投基线的基础上，抽取一部分"预投入本金"形成子弹，在深跌时集中打出去。
    - 永远在场：正常情况下，所有"未锁定现金"立刻买入，避免长期踏空。
    - 避免"隐形作弊"：收益率口径全部以【全部本金】为基准，不因囤钱美化收益。

    V9 新增特性：
    ----------------------------------------------------------------
    1. 多级深跌阈值：支持分档补仓，例如跌10%释放30%，跌15%释放50%，跌20%释放100%
    2. 连发机制：允许一轮下跌中多次补仓（反弹重置 / 冷却期重置）
    3. 动态预投入比例：低位少锁甚至不锁，高位多锁，避免在低位还攒子弹

    资金流转逻辑（简化版）：
    1. 每次有现金流入（定投日）：
       - 增加 principal_total（真实打入本金）
       - 按 effective_pre_invest_ratio 抽取一部分记入 pre_invest_locked（预投入锁定）
       - 剩余现金作为"可自由使用现金"（正常定投）

    2. 日度买入逻辑：
       - 任何时刻，locked_cash = min(pre_invest_locked, cash)
       - 可用于【普通买入】的现金 = cash - locked_cash
       - 基线行为：把这部分"未锁定现金"全部买入（模拟纯定投效果）

    3. 深跌补仓逻辑：
       - 使用 last_peak_nav 记录历史最高净值
       - 计算相对高点回撤 drawdown = (nav - last_peak_nav) / last_peak_nav
       - 根据阈值（单一或多级）决定释放比例
       - 支持连发：反弹一定幅度或冷却一定天数后可再次触发

    4. 峰值重置逻辑：
       - 当 nav 创历史新高时，更新 last_peak_nav 并可重置深跌触发标记

    可配置参数：
    ----------------------------------------------------------------
    【基础参数】
    - pre_invest_ratio: 锁定预投入比例（默认 0.10，启用动态预投入时作为后备）
    - deep_dip_threshold: 深跌阈值（默认 -0.10，单一阈值模式使用）
    - deep_dip_use_ratio: 深跌时释放比例（默认 0.50，单一阈值模式使用）
    - reset_on_new_high: 净值新高时重置深跌标记（默认 True）
    - allow_multi_deep_dip: 是否允许无视触发标记直接多次触发（默认 False）

    【连发机制参数】
    - rebound_reset_rate: 反弹重置率（默认 0.05，从上次补仓位置反弹5%可重置）
    - debounce_days: 冷却天数（默认 30，距上次补仓超过30天可重置）

    【多级阈值参数】
    - multi_stage_thresholds: 多级补仓档位列表（默认 None，使用单一阈值）
      示例: [{"threshold": -0.10, "use_ratio": 0.30}, {"threshold": -0.15, "use_ratio": 0.50}, ...]

    【动态预投入参数】
    - dynamic_pre_invest: 动态预投入配置（默认 None，使用固定比例）
      示例: {"enabled": True, "ma_window": 250, "low_zone_bias": 0.0, ...}
    """
    
    # 策略标识（用于注册表）
    strategy_key = "profit_recycle"
    strategy_version = "v10"
    display_name = "利润回收策略 v10 — 动态预投入 + 分级深跌补仓版"

    # =======================================================
    # 默认配置值
    # =======================================================
    DEFAULT_PRE_INVEST_RATIO = 0.10        # 锁定预投入比例
    DEFAULT_DEEP_DIP_THRESHOLD = -0.10     # 深跌阈值（V9 调低至 -10%）
    DEFAULT_DEEP_DIP_USE_RATIO = 0.50      # 深跌时释放比例
    DEFAULT_RESET_ON_NEW_HIGH = True       # 净值新高时重置深跌标记
    DEFAULT_ALLOW_MULTI_DEEP_DIP = False   # 是否允许无视标记多次触发
    DEFAULT_REBOUND_RESET_RATE = 0.05      # 反弹重置率（5%）
    DEFAULT_DEBOUNCE_DAYS = 30             # 冷却天数

    def __init__(self, config: Optional[Dict[str, Any]] = None) -> None:
        super().__init__(config or {})
        
        # ------------------------------------------------------------------
        # 基础配置参数
        # ------------------------------------------------------------------
        self.pre_invest_ratio: float = float(
            self.config.get("pre_invest_ratio", self.DEFAULT_PRE_INVEST_RATIO)
        )
        self.deep_dip_threshold: float = float(
            self.config.get("deep_dip_threshold", self.DEFAULT_DEEP_DIP_THRESHOLD)
        )
        self.deep_dip_use_ratio: float = float(
            self.config.get("deep_dip_use_ratio", self.DEFAULT_DEEP_DIP_USE_RATIO)
        )
        self.reset_on_new_high: bool = bool(
            self.config.get("reset_on_new_high", self.DEFAULT_RESET_ON_NEW_HIGH)
        )
        self.allow_multi_deep_dip: bool = bool(
            self.config.get("allow_multi_deep_dip", self.DEFAULT_ALLOW_MULTI_DEEP_DIP)
        )
        
        # ------------------------------------------------------------------
        # 连发机制参数
        # ------------------------------------------------------------------
        self.rebound_reset_rate: float = float(
            self.config.get("rebound_reset_rate", self.DEFAULT_REBOUND_RESET_RATE)
        )
        self.debounce_days: int = int(
            self.config.get("debounce_days", self.DEFAULT_DEBOUNCE_DAYS)
        )
        
        # ------------------------------------------------------------------
        # 多级阈值参数（新功能）
        # 格式: [{"threshold": -0.10, "use_ratio": 0.30}, ...]
        # 若为 None 或空列表，使用单一阈值模式
        # ------------------------------------------------------------------
        self.multi_stage_thresholds: Optional[List[Dict[str, float]]] = self.config.get(
            "multi_stage_thresholds", None
        )
        
        # ------------------------------------------------------------------
        # 动态预投入参数（新功能）
        # 格式: {"enabled": True, "ma_window": 250, "low_zone_bias": 0.0, ...}
        # 若为 None，使用固定 pre_invest_ratio
        # ------------------------------------------------------------------
        self.dynamic_pre_invest: Optional[Dict[str, Any]] = self.config.get(
            "dynamic_pre_invest", None
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
        state["deep_dip_triggered"] = False     # 当前这一波下跌中是否已经触发过一次深跌补仓
        state["deep_dip_count"] = 0             # 全回测周期内，深跌补仓触发次数
        
        # ------------------------------------------------------------------
        # V9 新增：连发机制相关状态
        # ------------------------------------------------------------------
        state["last_deep_dip_nav"] = 0.0        # 最近一次深跌补仓触发时的 nav
        state["last_deep_dip_date"] = None      # 最近一次深跌补仓的日期
        
        # ------------------------------------------------------------------
        # V9 新增：动态预投入相关状态
        # ------------------------------------------------------------------
        state["nav_history"] = []               # NAV 历史记录（用于计算均线）
        state["last_effective_pre_invest_ratio"] = self.pre_invest_ratio  # 最近一次生效的预投入比例
        state["last_nav_bias"] = 0.0            # 最近一次计算的估值偏离度

    # ------------------------------------------------------------------
    # 生命周期：逐日回测
    # ------------------------------------------------------------------
    def on_bar(self, ctx: Context) -> Signal:
        """
        每个交易日的决策逻辑
        """
        nav: float = ctx.nav
        date: datetime = ctx.date

        cash: float = ctx.cash  # 使用统一的 cash 字段
        cash_inflow: float = getattr(ctx, "cash_inflow", 0.0) or 0.0

        state = self.state
        note_parts: List[str] = []

        # ==================================================================
        # 步骤 0：记录 NAV 历史（用于动态预投入的均线计算）
        # ==================================================================
        nav_history: List[float] = state.get("nav_history", [])
        nav_history.append(nav)
        state["nav_history"] = nav_history

        # ==================================================================
        # 步骤 1：计算有效预投入比例（动态 or 固定）
        # ==================================================================
        effective_pre_invest_ratio = self._calc_effective_pre_invest_ratio(nav, nav_history)
        state["last_effective_pre_invest_ratio"] = effective_pre_invest_ratio

        # ==================================================================
        # 步骤 2：处理预投入本金（从当日现金流中抽取一部分记入锁定池）
        # ==================================================================
        reserve: float = 0.0
        if cash_inflow > 0 and effective_pre_invest_ratio > 0:
            reserve = cash_inflow * effective_pre_invest_ratio
            state["pre_invest_locked"] += reserve
            state["pre_invest_total_in"] += reserve
            note_parts.append(
                f"预投入划入: {reserve:.2f} 元 (比例={effective_pre_invest_ratio:.1%}) "
                f"-> 锁定池 ({state['pre_invest_locked']:.2f})"
            )

        # ==================================================================
        # 步骤 3：更新历史高点 & 计算回撤
        # ==================================================================
        last_peak_nav: float = state.get("last_peak_nav", 0.0)

        # 初始化高点
        if last_peak_nav <= 0.0:
            last_peak_nav = nav
            state["last_peak_nav"] = nav
            drawdown = 0.0
        else:
            if nav > last_peak_nav:
                # 创新高：更新峰值，并根据配置重置深跌触发标记
                last_peak_nav = nav
                state["last_peak_nav"] = nav
                if self.reset_on_new_high:
                    state["deep_dip_triggered"] = False
                drawdown = 0.0
                note_parts.append(f"净值创新高: {nav:.4f}")
            else:
                drawdown = (nav - last_peak_nav) / last_peak_nav

        # ==================================================================
        # 步骤 4：检查连发机制的重置条件
        # ==================================================================
        # 即使没有创新高，也可能满足以下重置条件之一：
        #   (a) 反弹重置：从上次补仓位置反弹超过 rebound_reset_rate
        #   (b) 冷却重置：距上次补仓超过 debounce_days 天
        #
        # 注意：allow_multi_deep_dip = True 时，不检查 deep_dip_triggered，
        #       只靠阈值和锁定池余额控制频率。
        #       allow_multi_deep_dip = False 时，依赖下面的重置逻辑。
        
        if not self.allow_multi_deep_dip and state.get("deep_dip_triggered", False):
            reset_reason = self._check_reset_conditions(nav, date, state)
            if reset_reason:
                state["deep_dip_triggered"] = False
                note_parts.append(f"深跌标记重置: {reset_reason}")

        # ==================================================================
        # 步骤 5：深跌补仓逻辑
        # ==================================================================
        # 判断是否可以触发深跌补仓：
        #   - allow_multi_deep_dip = True：不检查 deep_dip_triggered，只要满足阈值就触发
        #   - allow_multi_deep_dip = False：需要 deep_dip_triggered = False 才能触发
        
        can_trigger = self.allow_multi_deep_dip or not state.get("deep_dip_triggered", False)
        
        if can_trigger and last_peak_nav > 0.0 and state["pre_invest_locked"] > 0.0:
            # 计算本次应该释放的比例
            use_ratio = self._get_deep_dip_use_ratio(drawdown)
            
            if use_ratio > 0:
                # 计算释放金额
                want_use = state["pre_invest_locked"] * use_ratio
                deep_dip_amount = min(want_use, state["pre_invest_locked"], cash)
                
                if deep_dip_amount > 0:
                    # 执行释放
                    state["pre_invest_locked"] -= deep_dip_amount
                    state["pre_invest_released"] += deep_dip_amount
                    state["deep_dip_triggered"] = True
                    state["deep_dip_count"] = state.get("deep_dip_count", 0) + 1
                    
                    # 记录本次补仓的位置和时间（用于连发机制）
                    state["last_deep_dip_nav"] = nav
                    state["last_deep_dip_date"] = date
                    
                    note_parts.append(
                        f"深跌补仓触发: 回撤={drawdown:.2%}, 释放比例={use_ratio:.0%}, "
                        f"释放 {deep_dip_amount:.2f} 元"
                    )

        # ==================================================================
        # 步骤 6：计算普通买入可用的现金
        # ==================================================================
        # locked_cash 表示当前 cash 中，有多少需要被视为"仍然锁定"的预投入资金
        locked_cash = min(state["pre_invest_locked"], cash)
        normal_available_cash = max(0.0, cash - locked_cash)

        buy_cash = 0.0
        sell_units = 0.0

        # ==================================================================
        # 步骤 7：基线行为 —— 所有未锁定的现金，全部用于买入
        # ==================================================================
        if normal_available_cash > 0:
            buy_cash = normal_available_cash
            note_parts.append(
                f"常规买入: {buy_cash:.2f} 元 (可用={cash:.2f}, 锁定={locked_cash:.2f})"
            )

        # 本版本不做止盈卖出（sell_units 始终为 0）
        return Signal(
            buy_cash=buy_cash,
            sell_units=sell_units,
            note="; ".join(note_parts),
        )

    # ------------------------------------------------------------------
    # 辅助方法：计算有效预投入比例
    # ------------------------------------------------------------------
    def _calc_effective_pre_invest_ratio(self, nav: float, nav_history: List[float]) -> float:
        """
        根据动态预投入配置，计算当前有效的预投入比例。
        若未启用动态预投入，返回固定的 self.pre_invest_ratio。
        """
        # 检查是否启用动态预投入
        if not self.dynamic_pre_invest:
            return self.pre_invest_ratio
        
        if not self.dynamic_pre_invest.get("enabled", False):
            return self.pre_invest_ratio
        
        # 读取动态预投入配置
        ma_window = self.dynamic_pre_invest.get("ma_window", 250)
        low_zone_bias = self.dynamic_pre_invest.get("low_zone_bias", 0.0)
        high_zone_bias = self.dynamic_pre_invest.get("high_zone_bias", 0.20)
        low_ratio = self.dynamic_pre_invest.get("low_ratio", 0.0)
        normal_ratio = self.dynamic_pre_invest.get("normal_ratio", 0.05)
        high_ratio = self.dynamic_pre_invest.get("high_ratio", 0.20)
        
        # 如果历史数据不足，使用 normal_ratio 作为过渡
        # （避免在回测初期因数据不足而产生异常行为）
        if len(nav_history) < ma_window:
            self.state["last_nav_bias"] = 0.0
            return normal_ratio
        
        # 计算均线
        recent_navs = nav_history[-ma_window:]
        ma = sum(recent_navs) / len(recent_navs)
        
        # 计算偏离度
        bias = (nav - ma) / ma if ma > 0 else 0.0
        self.state["last_nav_bias"] = bias
        
        # 根据偏离度确定锁定比例
        if bias <= low_zone_bias:
            # 低位区：少锁或不锁
            return low_ratio
        elif bias >= high_zone_bias:
            # 高位区：多锁
            return high_ratio
        else:
            # 中性区域
            return normal_ratio

    # ------------------------------------------------------------------
    # 辅助方法：检查连发重置条件
    # ------------------------------------------------------------------
    def _check_reset_conditions(self, nav: float, date: datetime, state: Dict) -> Optional[str]:
        """
        检查是否满足连发机制的重置条件。
        返回重置原因字符串，若不满足则返回 None。
        """
        last_deep_dip_nav = state.get("last_deep_dip_nav", 0.0)
        last_deep_dip_date = state.get("last_deep_dip_date")
        
        # 条件 (a)：反弹重置
        # 从上次补仓位置反弹超过 rebound_reset_rate
        if last_deep_dip_nav > 0 and self.rebound_reset_rate > 0:
            rebound_threshold = last_deep_dip_nav * (1 + self.rebound_reset_rate)
            if nav >= rebound_threshold:
                return f"反弹至 {nav:.4f} >= {rebound_threshold:.4f}"
        
        # 条件 (b)：冷却重置
        # 距上次补仓超过 debounce_days 天
        if last_deep_dip_date is not None and self.debounce_days > 0:
            days_since = (date - last_deep_dip_date).days
            if days_since >= self.debounce_days:
                return f"冷却 {days_since} 天 >= {self.debounce_days} 天"
        
        return None

    # ------------------------------------------------------------------
    # 辅助方法：获取深跌释放比例
    # ------------------------------------------------------------------
    def _get_deep_dip_use_ratio(self, drawdown: float) -> float:
        """
        根据当前回撤，计算应该释放的比例。
        支持单一阈值模式和多级阈值模式。
        
        返回 0 表示不触发，返回 > 0 表示触发并释放相应比例。
        """
        # ------------------------------------------------------------------
        # 多级阈值模式
        # ------------------------------------------------------------------
        if self.multi_stage_thresholds and len(self.multi_stage_thresholds) > 0:
            # 按阈值从重到轻排序（threshold 数值最小的排前面）
            sorted_stages = sorted(
                self.multi_stage_thresholds, 
                key=lambda x: x.get("threshold", 0)
            )
            
            # 找到满足条件的最重档位
            for stage in sorted_stages:
                threshold = stage.get("threshold", -0.20)
                use_ratio = stage.get("use_ratio", 0.50)
                if drawdown <= threshold:
                    return use_ratio
            
            # 没有满足任何档位
            return 0.0
        
        # ------------------------------------------------------------------
        # 单一阈值模式（兼容 V8）
        # ------------------------------------------------------------------
        if drawdown <= self.deep_dip_threshold:
            return self.deep_dip_use_ratio
        
        return 0.0

    # ------------------------------------------------------------------
    # 策略名称
    # ------------------------------------------------------------------
    # ------------------------------------------------------------------
    # 策略元数据（已在类属性中定义，此处保留注释供参考）
    # ------------------------------------------------------------------
    # 策略统计信息
    # ------------------------------------------------------------------
    def get_stats(self) -> Dict[str, Any]:
        """
        返回策略专有统计信息，包含配置参数和运行时状态
        """
        stats = {
            # 基础配置参数
            "cfg_pre_invest_ratio": self.pre_invest_ratio,
            "cfg_deep_dip_threshold": self.deep_dip_threshold,
            "cfg_deep_dip_use_ratio": self.deep_dip_use_ratio,
            "cfg_reset_on_new_high": self.reset_on_new_high,
            "cfg_allow_multi_deep_dip": self.allow_multi_deep_dip,
            
            # 连发机制参数
            "cfg_rebound_reset_rate": self.rebound_reset_rate,
            "cfg_debounce_days": self.debounce_days,
            
            # 多级阈值模式标记
            "cfg_multi_stage_enabled": bool(self.multi_stage_thresholds and len(self.multi_stage_thresholds) > 0),
            
            # 动态预投入模式标记
            "cfg_dynamic_pre_invest_enabled": bool(
                self.dynamic_pre_invest and self.dynamic_pre_invest.get("enabled", False)
            ),
            
            # 运行时状态
            "pre_invest_locked": self.state.get("pre_invest_locked", 0.0),
            "pre_invest_total_in": self.state.get("pre_invest_total_in", 0.0),
            "pre_invest_released": self.state.get("pre_invest_released", 0.0),
            "deep_dip_count": self.state.get("deep_dip_count", 0),
            "last_peak_nav": self.state.get("last_peak_nav", 0.0),
            
            # V9 新增状态
            "nav_history_len": len(self.state.get("nav_history", [])),
            "last_effective_pre_invest_ratio": self.state.get("last_effective_pre_invest_ratio", self.pre_invest_ratio),
            "last_nav_bias": self.state.get("last_nav_bias", 0.0),
        }
        
        return stats

    # ------------------------------------------------------------------
    # 回测结果汇总打印
    # ------------------------------------------------------------------
    def render_summary(self, summary: Dict[str, Any]) -> None:
        """
        对回测引擎传入的 summary 做结果展示
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
        
        # ------------------------------------------------------------------
        # 策略配置参数
        # ------------------------------------------------------------------
        print()
        print("【策略配置参数】")
        
        # 判断使用哪种模式
        multi_stage_enabled = bool(self.multi_stage_thresholds and len(self.multi_stage_thresholds) > 0)
        dynamic_enabled = bool(self.dynamic_pre_invest and self.dynamic_pre_invest.get("enabled", False))
        
        if multi_stage_enabled:
            print(f"   深跌模式: 多级阈值模式")
            for i, stage in enumerate(self.multi_stage_thresholds):
                threshold = stage.get("threshold", 0)
                use_ratio = stage.get("use_ratio", 0)
                print(f"      档位{i+1}: 回撤 ≤ {threshold:.0%} 释放 {use_ratio:.0%}")
        else:
            print(f"   深跌模式: 单一阈值模式")
            print(f"   深跌阈值 (deep_dip_threshold):         {self.deep_dip_threshold:.2%}")
            print(f"   深跌释放比例 (deep_dip_use_ratio):     {self.deep_dip_use_ratio:.2%}")
        
        if dynamic_enabled:
            ma_window = self.dynamic_pre_invest.get("ma_window", 250)
            print(f"   预投入模式: 动态预投入（按 MA{ma_window} + 高低估分区自动调整）")
            print(f"      低位锁定: {self.dynamic_pre_invest.get('low_ratio', 0):.0%}")
            print(f"      中性锁定: {self.dynamic_pre_invest.get('normal_ratio', 0.05):.0%}")
            print(f"      高位锁定: {self.dynamic_pre_invest.get('high_ratio', 0.2):.0%}")
        else:
            print(f"   预投入模式: 固定比例")
            print(f"   预投入锁定比例 (pre_invest_ratio):     {self.pre_invest_ratio:.2%}")
        
        print(f"   新高重置深跌标记 (reset_on_new_high):  {self.reset_on_new_high}")
        print(f"   允许连续深跌 (allow_multi_deep_dip):   {self.allow_multi_deep_dip}")
        print(f"   反弹重置率 (rebound_reset_rate):       {self.rebound_reset_rate:.2%}")
        print(f"   冷却天数 (debounce_days):              {self.debounce_days} 天")
        
        # ------------------------------------------------------------------
        # 资金情况
        # ------------------------------------------------------------------
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
        
        # ------------------------------------------------------------------
        # 收益情况
        # ------------------------------------------------------------------
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
        
        # ------------------------------------------------------------------
        # 交易统计
        # ------------------------------------------------------------------
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
        
        # ------------------------------------------------------------------
        # 内部资金池状态
        # ------------------------------------------------------------------
        pre_invest_locked = summary.get("pre_invest_locked", 0.0)
        pre_invest_total_in = summary.get("pre_invest_total_in", 0.0)
        pre_invest_released = summary.get("pre_invest_released", 0.0)
        deep_dip_count = summary.get("deep_dip_count", 0)
        last_peak_nav = summary.get("last_peak_nav", 0.0)
        
        print()
        print("【内部资金池状态】")
        print(f"   预投入锁定余额:              {pre_invest_locked:>12,.2f} 元")
        print(f"   预投入历史累计划入:          {pre_invest_total_in:>12,.2f} 元")
        print(f"   深跌已释放总额:              {pre_invest_released:>12,.2f} 元")
        print(f"   深跌补仓触发次数:                  {deep_dip_count:>6d} 次")
        print(f"   观测最高净值:                     {last_peak_nav:>8.4f}")
        
        # V9 新增状态
        nav_history_len = summary.get("nav_history_len", len(self.state.get("nav_history", [])))
        last_eff_ratio = summary.get("last_effective_pre_invest_ratio", 
                                      self.state.get("last_effective_pre_invest_ratio", self.pre_invest_ratio))
        last_bias = summary.get("last_nav_bias", self.state.get("last_nav_bias", 0.0))
        
        if dynamic_enabled:
            print(f"   NAV 历史长度:                     {nav_history_len:>6d}")
            print(f"   最后生效预投入比例:               {last_eff_ratio:>8.2%}")
            print(f"   最后估值偏离度:                   {last_bias:>8.2%}")
        
        # ------------------------------------------------------------------
        # 校准视角
        # ------------------------------------------------------------------
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
    # 档位信息
    # =======================================================
    def get_levels_info(self) -> Dict[str, Any]:
        """
        返回策略的"档位"说明，供 run_backtest 做展示。
        """
        multi_stage_enabled = bool(self.multi_stage_thresholds and len(self.multi_stage_thresholds) > 0)
        dynamic_enabled = bool(self.dynamic_pre_invest and self.dynamic_pre_invest.get("enabled", False))
        
        # 生成深跌档位描述
        dip_levels_desc = []
        
        if multi_stage_enabled:
            for stage in self.multi_stage_thresholds:
                threshold = stage.get("threshold", 0)
                use_ratio = stage.get("use_ratio", 0)
                dip_levels_desc.append(
                    f"深跌档位: 回撤 ≥ {abs(threshold):.0%} 释放 {use_ratio:.0%} 锁定资金"
                )
        else:
            dip_levels_desc.append(f"深跌阈值: 回撤 ≥ {abs(self.deep_dip_threshold):.0%}")
            dip_levels_desc.append(f"深跌触发时释放预投入锁定池的 {self.deep_dip_use_ratio:.0%}")
        
        # 预投入描述
        if dynamic_enabled:
            ma_window = self.dynamic_pre_invest.get("ma_window", 250)
            low_ratio = self.dynamic_pre_invest.get("low_ratio", 0)
            high_ratio = self.dynamic_pre_invest.get("high_ratio", 0.2)
            dip_levels_desc.append(
                f"动态预投入: 低位锁 {low_ratio:.0%}，高位锁 {high_ratio:.0%}（MA{ma_window}）"
            )
        else:
            dip_levels_desc.append(f"预投入比例: 每笔现金流入的 {self.pre_invest_ratio:.0%} 划入锁定池")
        
        # 连发机制描述
        dip_levels_desc.append(
            f"连发机制: 反弹 {self.rebound_reset_rate:.0%} 或冷却 {self.debounce_days} 天可重置"
        )
        
        # 构建描述文本
        if multi_stage_enabled and dynamic_enabled:
            description = "v10：动态预投入 + 分级深跌补仓，低位不锁，高位多锁，支持连发。"
        elif multi_stage_enabled:
            description = "v10：分级深跌补仓版，支持连发机制。"
        elif dynamic_enabled:
            description = "v10：动态预投入版，低位不锁，高位多锁，深跌补仓。"
        else:
            description = "v10：基础增强版，深跌补仓 + 连发机制。"
        
        return {
            "mode": "profit_recycle_v9",
            "tp_levels_desc": [
                "当前版本不做止盈卖出"
            ],
            "dip_levels_desc": dip_levels_desc,
            "description": description,
        }
