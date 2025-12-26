# -*- coding: utf-8 -*-
"""
ProfitRecycleStrategy - 利润回收策略 v11（新框架版）

核心理念（保持 v11 不变）：
1) 动态锁定池（弹药池/等待池思想）：
   - 低估区：尽量少锁定（更多投入）
   - 中性区：少量锁定
   - 高估区：较多锁定（减少追高，把现金变成弹药）

2) 深跌分级释放弹药：
   - 触发回撤档位时，从锁定池释放一定比例投入

3) 高估区小比例“收割波动”（SELL）形成弹药：
   - 在偏离 MA 足够高且接近峰值时，卖出少量仓位，把回笼的现金计入锁定池
   - 同日避免“买卖互搏”（卖出日不买入）

注意：
- 本策略只输出“决策”（Decision），不做任何下单。
- 对 SELL：Decision.action="SELL"，target_amount 表示“目标卖出金额”（引擎可按 price 换算份额）。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

from ..framework.base import Strategy
from ..framework.context import Context
from ..framework.decision import Decision
from ..framework.registry import register_strategy


@dataclass
class DeepDipLevel:
    """深跌档位：回撤阈值（负数）+ 释放比例（0~1）"""
    threshold: float  # e.g. -0.10 表示回撤>=10%
    use_ratio: float  # e.g. 0.5 表示释放锁定池 50%


@register_strategy("profit_recycle", version="v11", set_as_default=True)
class ProfitRecycleStrategyV11(Strategy):
    """
    利润回收策略 v11（新框架版）

    输出口径：
    - BUY: target_amount 为“建议买入金额”
    - SELL: target_amount 为“建议卖出金额”（非份额）
    - HOLD: target_amount=0
    """

    strategy_key = "profit_recycle"
    strategy_version = "v11"
    display_name = "利润回收策略"

    # ======== 默认参数 ========
    DEFAULT_MA_WINDOW = 250

    # 估值分区
    DEFAULT_HIGH_BIAS = 0.20  # nav > MA*(1+high_bias) 视作高估区

    # 动态锁定比例（锁定的是“现金池的一部分”）
    DEFAULT_LOCK_RATIO_LOW = 0.00
    DEFAULT_LOCK_RATIO_MID = 0.05
    DEFAULT_LOCK_RATIO_HIGH = 0.20

    # 深跌分级释放（阈值用负数：-0.10 表示回撤 10%）
    DEFAULT_DEEP_DIP_LEVELS = [
        {"threshold": -0.10, "use_ratio": 0.50},
        {"threshold": -0.15, "use_ratio": 1.00},
    ]
    DEFAULT_ALLOW_MULTI_DEEP_DIP = True
    DEFAULT_REBOUND_RESET_RATE = 0.05
    DEFAULT_DEBOUNCE_DAYS = 30

    # 高估收割
    DEFAULT_TAKE_PROFIT_ENABLED = True
    DEFAULT_TAKE_PROFIT_BIAS = 0.18
    DEFAULT_TAKE_PROFIT_SELL_RATIO = 0.05
    DEFAULT_TAKE_PROFIT_COOLDOWN_DAYS = 60
    DEFAULT_NEAR_PEAK_RATIO = 0.98

    # 溢价刹车（QDII ETF）
    DEFAULT_PREMIUM_BRAKE_ENABLED = True
    DEFAULT_PREMIUM_T1 = 0.01  # <=1% 正常买
    DEFAULT_PREMIUM_T2 = 0.02  # (1%,2%] 买一半
    # >2% 不买

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        super().__init__(config)

        # ========= 均线/估值分区 =========
        self.ma_window: int = int(self.config.get("ma_window", self.DEFAULT_MA_WINDOW))
        self.high_bias: float = float(self.config.get("high_bias", self.DEFAULT_HIGH_BIAS))

        # ========= 动态锁定池 =========
        self.lock_ratio_low: float = float(self.config.get("lock_ratio_low", self.DEFAULT_LOCK_RATIO_LOW))
        self.lock_ratio_mid: float = float(self.config.get("lock_ratio_mid", self.DEFAULT_LOCK_RATIO_MID))
        self.lock_ratio_high: float = float(self.config.get("lock_ratio_high", self.DEFAULT_LOCK_RATIO_HIGH))

        # ========= 深跌分级释放 =========
        levels_cfg = self.config.get("deep_dip_levels", self.DEFAULT_DEEP_DIP_LEVELS)
        # 如果输入是字符串（JSON格式），解析它
        if isinstance(levels_cfg, str):
            try:
                levels_cfg = json.loads(levels_cfg)
            except (json.JSONDecodeError, TypeError) as e:
                # 如果解析失败，使用默认值
                levels_cfg = self.DEFAULT_DEEP_DIP_LEVELS
        # 确保是列表格式
        if not isinstance(levels_cfg, list):
            levels_cfg = self.DEFAULT_DEEP_DIP_LEVELS
        
        self.deep_dip_levels: List[DeepDipLevel] = [
            DeepDipLevel(float(x["threshold"]), float(x["use_ratio"])) for x in levels_cfg
        ]
        # 阈值越“深”越靠后（-0.15 < -0.10），我们需要能选到最深满足档位
        self.deep_dip_levels.sort(key=lambda x: x.threshold)

        self.allow_multi_deep_dip: bool = bool(self.config.get("allow_multi_deep_dip", self.DEFAULT_ALLOW_MULTI_DEEP_DIP))
        self.rebound_reset_rate: float = float(self.config.get("rebound_reset_rate", self.DEFAULT_REBOUND_RESET_RATE))
        self.debounce_days: int = int(self.config.get("debounce_days", self.DEFAULT_DEBOUNCE_DAYS))

        # ========= 高估收割 =========
        self.take_profit_enabled: bool = bool(self.config.get("take_profit_enabled", self.DEFAULT_TAKE_PROFIT_ENABLED))
        self.take_profit_bias: float = float(self.config.get("take_profit_bias", self.DEFAULT_TAKE_PROFIT_BIAS))
        self.take_profit_sell_ratio: float = float(self.config.get("take_profit_sell_ratio", self.DEFAULT_TAKE_PROFIT_SELL_RATIO))
        self.take_profit_cooldown_days: int = int(self.config.get("take_profit_cooldown_days", self.DEFAULT_TAKE_PROFIT_COOLDOWN_DAYS))
        self.near_peak_ratio: float = float(self.config.get("near_peak_ratio", self.DEFAULT_NEAR_PEAK_RATIO))

        # ========= 溢价刹车 =========
        self.premium_brake_enabled: bool = bool(self.config.get("premium_brake_enabled", self.DEFAULT_PREMIUM_BRAKE_ENABLED))
        self.premium_t1: float = float(self.config.get("premium_t1", self.DEFAULT_PREMIUM_T1))
        self.premium_t2: float = float(self.config.get("premium_t2", self.DEFAULT_PREMIUM_T2))

        # ========= state keys =========
        self.K_NAV_HISTORY = "nav_history"
        self.K_PEAK_NAV = "peak_nav"

        self.K_LOCKED_POOL = "locked_pool"  # 锁定池余额（策略内记账）
        self.K_DEEP_DIP_TRIGGERED = "deep_dip_triggered"
        self.K_LAST_DIP_DATE = "last_deep_dip_date"
        self.K_LAST_DIP_NAV = "last_deep_dip_nav"
        self.K_DEEP_DIP_COUNT = "deep_dip_count"

        self.K_LAST_TP_DATE = "last_take_profit_date"
        self.K_TP_COUNT = "take_profit_count"

        self.K_LAST_LOCK_RATIO = "last_lock_ratio"
        self.K_LAST_NAV_BIAS = "last_nav_bias"

    def on_start(self) -> None:
        self.state[self.K_NAV_HISTORY] = []
        self.state[self.K_PEAK_NAV] = None

        self.state[self.K_LOCKED_POOL] = 0.0

        self.state[self.K_DEEP_DIP_TRIGGERED] = False
        self.state[self.K_LAST_DIP_DATE] = None
        self.state[self.K_LAST_DIP_NAV] = 0.0
        self.state[self.K_DEEP_DIP_COUNT] = 0

        self.state[self.K_LAST_TP_DATE] = None
        self.state[self.K_TP_COUNT] = 0

        self.state[self.K_LAST_LOCK_RATIO] = 0.0
        self.state[self.K_LAST_NAV_BIAS] = 0.0

    def on_day(self, ctx: Context) -> Decision:
        nav = float(ctx.close)
        today = ctx.date

        # 组合/资金
        cash_pool = float(ctx.cash_pool or 0.0)
        wait_pool = float(ctx.wait_pool or 0.0)
        total_cash_pool = max(0.0, cash_pool + wait_pool)

        holdings = ctx.holdings or {}
        shares = float(holdings.get("shares", 0.0) or 0.0)
        holding_value = float(holdings.get("value", 0.0) or 0.0)

        reasons: List[str] = []

        # ===== 维护 NAV 历史 / MA =====
        nav_hist: List[float] = self.state.get(self.K_NAV_HISTORY, [])
        nav_hist.append(nav)
        # 保留足够长度
        cap = max(self.ma_window * 3, self.ma_window + 10)
        if len(nav_hist) > cap:
            nav_hist = nav_hist[-cap:]
        self.state[self.K_NAV_HISTORY] = nav_hist

        ma: Optional[float] = None
        if len(nav_hist) >= self.ma_window:
            ma = sum(nav_hist[-self.ma_window:]) / self.ma_window

        # ===== 峰值与回撤 =====
        peak = self.state.get(self.K_PEAK_NAV, None)
        if peak is None:
            peak = nav
        peak = float(peak)

        if nav > peak:
            peak = nav
        self.state[self.K_PEAK_NAV] = peak

        drawdown = 0.0
        if peak > 1e-12:
            drawdown = (nav - peak) / peak  # 负数=回撤
        drawdown_pct = abs(drawdown) * 100

        # ===== 估值偏离 / 动态锁定比例 =====
        nav_bias = 0.0
        if ma is not None and ma > 1e-12:
            nav_bias = (nav - ma) / ma
        self.state[self.K_LAST_NAV_BIAS] = nav_bias

        lock_ratio = self.lock_ratio_mid
        if ma is not None:
            if nav < ma:
                lock_ratio = self.lock_ratio_low
            elif nav > ma * (1.0 + self.high_bias):
                lock_ratio = self.lock_ratio_high
            else:
                lock_ratio = self.lock_ratio_mid
        self.state[self.K_LAST_LOCK_RATIO] = lock_ratio

        # ===== 锁定池目标（用“当前总现金池×锁定比例”定义，不依赖 cash_inflow）=====
        locked_pool = float(self.state.get(self.K_LOCKED_POOL, 0.0) or 0.0)
        target_locked = min(total_cash_pool, total_cash_pool * lock_ratio)

        # 如果目标锁定下降（低估/回落），释放差额到“可买入预算”
        unlock_release = 0.0
        if locked_pool > target_locked + 1e-8:
            unlock_release = locked_pool - target_locked
            locked_pool = target_locked
            reasons.append(f"估值转弱，锁定池下降，释放 {unlock_release:.2f} 元用于买入")
        # 如果目标锁定上升（高估），把差额“锁住”（本质就是减少本日买入预算）
        elif locked_pool < target_locked - 1e-8:
            inc = target_locked - locked_pool
            locked_pool = target_locked
            reasons.append(f"估值偏高，增加锁定池 {inc:.2f} 元（减少追高投入）")

        # ===== 深跌触发：从锁定池释放弹药 =====
        deep_dip_triggered = bool(self.state.get(self.K_DEEP_DIP_TRIGGERED, False))
        last_dip_date = self.state.get(self.K_LAST_DIP_DATE, None)
        last_dip_nav = float(self.state.get(self.K_LAST_DIP_NAV, 0.0) or 0.0)

        # 允许连发：反弹 or 冷却到期
        if deep_dip_triggered and self.allow_multi_deep_dip:
            can_reset = False
            if last_dip_nav > 1e-12 and nav >= last_dip_nav * (1.0 + self.rebound_reset_rate):
                can_reset = True
            if last_dip_date is not None:
                try:
                    if (today - last_dip_date).days >= self.debounce_days:
                        can_reset = True
                except Exception:
                    pass
            if can_reset:
                deep_dip_triggered = False
                self.state[self.K_DEEP_DIP_TRIGGERED] = False

        deep_dip_release = 0.0
        if not deep_dip_triggered and locked_pool > 1e-8:
            matched: Optional[DeepDipLevel] = None
            # 选择“最深满足档位”（threshold 更小/更负）
            for lvl in self.deep_dip_levels:
                if drawdown <= lvl.threshold:
                    matched = lvl
            if matched is not None:
                deep_dip_release = locked_pool * matched.use_ratio
                deep_dip_release = max(0.0, min(deep_dip_release, locked_pool))
                locked_pool -= deep_dip_release

                self.state[self.K_DEEP_DIP_TRIGGERED] = True
                self.state[self.K_LAST_DIP_DATE] = today
                self.state[self.K_LAST_DIP_NAV] = nav
                self.state[self.K_DEEP_DIP_COUNT] = int(self.state.get(self.K_DEEP_DIP_COUNT, 0) or 0) + 1

                reasons.append(
                    f"回撤 {drawdown_pct:.2f}% 触发深跌档位(≤{abs(matched.threshold)*100:.0f}%)，"
                    f"释放锁定池 {matched.use_ratio*100:.0f}% = {deep_dip_release:.2f} 元"
                )

        # 回写锁定池
        self.state[self.K_LOCKED_POOL] = float(max(0.0, locked_pool))

        # ===== 高估收割：SELL 形成弹药（卖出所得计入锁定池）=====
        if self.take_profit_enabled and shares > 1e-12 and ma is not None:
            near_peak = nav >= peak * self.near_peak_ratio
            tp_ok = (nav_bias >= self.take_profit_bias) and near_peak

            # 冷却
            if tp_ok:
                last_tp_date = self.state.get(self.K_LAST_TP_DATE, None)
                if last_tp_date is not None:
                    try:
                        if (today - last_tp_date).days < self.take_profit_cooldown_days:
                            tp_ok = False
                    except Exception:
                        tp_ok = False

            if tp_ok and holding_value > 1e-8:
                sell_amount = holding_value * self.take_profit_sell_ratio
                sell_amount = max(0.0, min(sell_amount, holding_value))

                # 估算卖出回笼计入锁定池（弹药）
                self.state[self.K_LOCKED_POOL] = float(self.state.get(self.K_LOCKED_POOL, 0.0) or 0.0) + sell_amount
                self.state[self.K_LAST_TP_DATE] = today
                self.state[self.K_TP_COUNT] = int(self.state.get(self.K_TP_COUNT, 0) or 0) + 1

                return Decision(
                    action="SELL",
                    target_amount=float(sell_amount),
                    reasons=[
                        f"高估收割触发：nav_bias={nav_bias*100:.2f}% ≥ {self.take_profit_bias*100:.2f}%",
                        f"接近峰值：nav={nav:.4f}，peak={peak:.4f}，near_peak_ratio={self.near_peak_ratio*100:.1f}%",
                        f"建议卖出金额 {sell_amount:.2f}（约占持仓市值 {self.take_profit_sell_ratio*100:.2f}%），卖出回笼计入锁定池作为弹药",
                    ],
                    tags=["profit_recycle", "take_profit", "sell"]
                )

        # ===== BUY 预算：总现金池 - 锁定池 +（估值释放/深跌释放）=====
        # 这里不直接“移动池子”，只通过“减少/增加本日买入金额”实现策略意图
        # 基础可用预算：把锁定池从总现金池中扣掉
        base_buy_budget = max(0.0, total_cash_pool - float(self.state.get(self.K_LOCKED_POOL, 0.0) or 0.0))
        buy_budget = base_buy_budget + unlock_release + deep_dip_release

        # 不能超过总现金池（避免出现策略建议“花超了”的错觉）
        buy_budget = max(0.0, min(buy_budget, total_cash_pool))

        # ===== 溢价刹车（如果有 premium_rate）=====
        if self.premium_brake_enabled and ctx.premium_rate is not None and buy_budget > 1e-8:
            pr = float(ctx.premium_rate)
            if pr <= self.premium_t1:
                reasons.append(f"溢价 {pr*100:.2f}% ≤ {self.premium_t1*100:.2f}%：正常买入")
                # 不变
            elif pr <= self.premium_t2:
                half = buy_budget * 0.5
                hold = buy_budget - half
                buy_budget = half
                # “剩余”不买相当于回到锁定池/等待池
                self.state[self.K_LOCKED_POOL] = float(self.state.get(self.K_LOCKED_POOL, 0.0) or 0.0) + hold
                reasons.append(f"溢价 {pr*100:.2f}% ∈ ({self.premium_t1*100:.2f}%, {self.premium_t2*100:.2f}%]：买一半 {half:.2f}，剩余 {hold:.2f} 进入锁定池等待")
            else:
                hold = buy_budget
                buy_budget = 0.0
                self.state[self.K_LOCKED_POOL] = float(self.state.get(self.K_LOCKED_POOL, 0.0) or 0.0) + hold
                reasons.append(f"溢价 {pr*100:.2f}% > {self.premium_t2*100:.2f}%：不买，全部 {hold:.2f} 进入锁定池等待")

        # ===== 输出 BUY / HOLD =====
        if buy_budget > 1e-6:
            if ma is None:
                reasons.append("MA 未就绪（历史不足），按现金预算买入（策略仍会记录峰值/锁定池）")
            else:
                reasons.append(f"MA({self.ma_window})={ma:.4f}，nav={nav:.4f}，nav_bias={nav_bias*100:.2f}%")
            reasons.append(f"总现金池={total_cash_pool:.2f}，锁定池={float(self.state.get(self.K_LOCKED_POOL, 0.0)):.2f}，建议买入={buy_budget:.2f}")

            return Decision(
                action="BUY",
                target_amount=float(buy_budget),
                reasons=reasons or ["满足买入条件"],
                tags=["profit_recycle", "buy"]
            )

        # HOLD
        if ma is None:
            reasons.append("MA 未就绪，且当前不建议买入（预算为0）")
        else:
            reasons.append(f"当前回撤 {drawdown_pct:.2f}%，nav_bias={nav_bias*100:.2f}%：无买入触发")
        reasons.append(f"锁定池余额 {float(self.state.get(self.K_LOCKED_POOL, 0.0)):.2f}（等待更好时机/深跌释放）")

        return Decision(
            action="HOLD",
            target_amount=0.0,
            reasons=reasons,
            tags=["profit_recycle", "hold"]
        )

    def get_default_params(self) -> Dict[str, Any]:
        return {
            "ma_window": self.DEFAULT_MA_WINDOW,
            "high_bias": self.DEFAULT_HIGH_BIAS,

            "lock_ratio_low": self.DEFAULT_LOCK_RATIO_LOW,
            "lock_ratio_mid": self.DEFAULT_LOCK_RATIO_MID,
            "lock_ratio_high": self.DEFAULT_LOCK_RATIO_HIGH,

            "deep_dip_levels": self.DEFAULT_DEEP_DIP_LEVELS,
            "allow_multi_deep_dip": self.DEFAULT_ALLOW_MULTI_DEEP_DIP,
            "rebound_reset_rate": self.DEFAULT_REBOUND_RESET_RATE,
            "debounce_days": self.DEFAULT_DEBOUNCE_DAYS,

            "take_profit_enabled": self.DEFAULT_TAKE_PROFIT_ENABLED,
            "take_profit_bias": self.DEFAULT_TAKE_PROFIT_BIAS,
            "take_profit_sell_ratio": self.DEFAULT_TAKE_PROFIT_SELL_RATIO,
            "take_profit_cooldown_days": self.DEFAULT_TAKE_PROFIT_COOLDOWN_DAYS,
            "near_peak_ratio": self.DEFAULT_NEAR_PEAK_RATIO,

            "premium_brake_enabled": self.DEFAULT_PREMIUM_BRAKE_ENABLED,
            "premium_t1": self.DEFAULT_PREMIUM_T1,
            "premium_t2": self.DEFAULT_PREMIUM_T2,
        }

    def get_param_schema(self) -> Dict[str, Any]:
        # 结构风格对齐 DrawdownStrategy
        return {
            "ma_window": {
                "type": "int",
                "default": self.DEFAULT_MA_WINDOW,
                "description": "均线窗口（交易日数），用于估值分区与偏离度计算",
                "min": 20,
                "max": 2000
            },
            "high_bias": {
                "type": "float",
                "default": self.DEFAULT_HIGH_BIAS,
                "description": "高估区阈值：nav > MA*(1+high_bias) 判定为高估区",
                "min": 0.0,
                "max": 1.0
            },
            "lock_ratio_low": {
                "type": "float",
                "default": self.DEFAULT_LOCK_RATIO_LOW,
                "description": "低估区锁定比例（锁定现金池的一部分，不参与买入）",
                "min": 0.0,
                "max": 1.0
            },
            "lock_ratio_mid": {
                "type": "float",
                "default": self.DEFAULT_LOCK_RATIO_MID,
                "description": "中性区锁定比例",
                "min": 0.0,
                "max": 1.0
            },
            "lock_ratio_high": {
                "type": "float",
                "default": self.DEFAULT_LOCK_RATIO_HIGH,
                "description": "高估区锁定比例（减少追高，形成弹药）",
                "min": 0.0,
                "max": 1.0
            },
            "deep_dip_levels": {
                "type": "str",
                "default": json.dumps(self.DEFAULT_DEEP_DIP_LEVELS, ensure_ascii=False),
                "description": "深跌档位列表（JSON格式），threshold 为负数（如 -0.10），use_ratio 为释放比例。示例：[{\"threshold\": -0.10, \"use_ratio\": 0.50}, {\"threshold\": -0.15, \"use_ratio\": 1.00}]"
            },
            "allow_multi_deep_dip": {
                "type": "bool",
                "default": self.DEFAULT_ALLOW_MULTI_DEEP_DIP,
                "description": "是否允许深跌连发（反弹一定比例或冷却期到达后重置触发）"
            },
            "rebound_reset_rate": {
                "type": "float",
                "default": self.DEFAULT_REBOUND_RESET_RATE,
                "description": "深跌触发后，反弹达到该比例则允许重置触发",
                "min": 0.0,
                "max": 1.0
            },
            "debounce_days": {
                "type": "int",
                "default": self.DEFAULT_DEBOUNCE_DAYS,
                "description": "深跌触发冷却天数，到期允许重置触发",
                "min": 0,
                "max": 3650
            },
            "take_profit_enabled": {
                "type": "bool",
                "default": self.DEFAULT_TAKE_PROFIT_ENABLED,
                "description": "是否启用高估收割（SELL）形成弹药池"
            },
            "take_profit_bias": {
                "type": "float",
                "default": self.DEFAULT_TAKE_PROFIT_BIAS,
                "description": "收割触发偏离阈值：nav_bias >= take_profit_bias",
                "min": 0.0,
                "max": 2.0
            },
            "take_profit_sell_ratio": {
                "type": "float",
                "default": self.DEFAULT_TAKE_PROFIT_SELL_RATIO,
                "description": "收割卖出比例（按持仓市值比例换算卖出金额）",
                "min": 0.0,
                "max": 1.0
            },
            "take_profit_cooldown_days": {
                "type": "int",
                "default": self.DEFAULT_TAKE_PROFIT_COOLDOWN_DAYS,
                "description": "收割冷却天数，避免频繁交易",
                "min": 0,
                "max": 3650
            },
            "near_peak_ratio": {
                "type": "float",
                "default": self.DEFAULT_NEAR_PEAK_RATIO,
                "description": "接近峰值判定：nav >= peak * near_peak_ratio",
                "min": 0.5,
                "max": 1.0
            },
            "premium_brake_enabled": {
                "type": "bool",
                "default": self.DEFAULT_PREMIUM_BRAKE_ENABLED,
                "description": "是否启用溢价刹车（ctx.premium_rate 有值时生效）"
            },
            "premium_t1": {
                "type": "float",
                "default": self.DEFAULT_PREMIUM_T1,
                "description": "溢价阈值1：<=t1 正常买",
                "min": 0.0,
                "max": 0.2
            },
            "premium_t2": {
                "type": "float",
                "default": self.DEFAULT_PREMIUM_T2,
                "description": "溢价阈值2：(t1,t2] 买一半，>t2 不买",
                "min": 0.0,
                "max": 0.2
            },
        }
