"""
主人策略 v1 — 兴全合润专用策略

包含：
- 每月定投500本金
- 三段式止盈
- 盈利池 × 低估补仓机制
- NAV高点记录
- 本金永不卖出
"""

from typing import Dict, Any
from .base import Strategy, Context, Signal


class MasterStrategyV1(Strategy):
    """
    主人策略 v1 — 兴全合润专用策略
    --------------------------------
    包含：
      - 每月定投500本金
      - 三段式止盈
      - 盈利池 × 低估补仓机制
      - NAV高点记录
      - 本金永不卖出
    """

    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(config)
        self.monthly_invest = self.config.get('monthly_invest', 500.0)

    def on_start(self) -> None:
        """初始化策略状态"""
        self.state['last_peak_nav'] = None   # 最近高点 NAV
        self.state['total_cost'] = 0.0       # 本金总投入
        self.state['prev_month'] = None      # 上一个月份
        self.state['units'] = 0.0            # 内部记录份额（用于计算）
        self.state['cash_pool'] = 0.0        # 内部现金池（用于计算）

    def on_bar(self, ctx: Context) -> Signal:
        """每日被回测引擎调用"""
        date = ctx.date
        nav = ctx.nav
        
        # 初始化高点
        if self.state['last_peak_nav'] is None:
            self.state['last_peak_nav'] = nav

        # 同步份额和现金池（从 portfolio 读取真实值）
        self.state['units'] = ctx.portfolio.units
        self.state['cash_pool'] = ctx.cash_pool
        self.state['total_cost'] = ctx.portfolio.total_cost

        # 用于记录本次操作
        buy_cash = 0.0
        sell_units = 0.0
        notes = []

        # 每月第一个交易日增加本金（通过 cash_inflow 自动处理，这里只记录）
        month_key = (date.year, date.month)
        if self.state['prev_month'] is None or month_key != self.state['prev_month']:
            self.state['prev_month'] = month_key
            # 引擎已自动将 periodic_invest 加入 cash_pool

        # 检查止盈
        tp_result = self._check_take_profit(nav)
        if tp_result['sell_units'] > 0:
            sell_units = tp_result['sell_units']
            notes.append(tp_result['note'])

        # 检查低估补仓（如果没有止盈）
        if sell_units == 0:
            dip_result = self._check_dip_buy(nav, ctx.cash_pool)
            if dip_result['buy_cash'] > 0:
                buy_cash = dip_result['buy_cash']
                notes.append(dip_result['note'])

        # 更新高点（止盈或补仓后都要更新）
        if nav > self.state['last_peak_nav']:
            self.state['last_peak_nav'] = nav

        return Signal(
            buy_cash=buy_cash,
            sell_units=sell_units,
            note="; ".join(notes) if notes else ""
        )

    # -------------------------------------------------------
    # 止盈逻辑（三段式）
    # -------------------------------------------------------
    def _check_take_profit(self, nav) -> Dict:
        """检查止盈条件"""
        result = {'sell_units': 0.0, 'note': ''}
        
        last_peak_nav = self.state['last_peak_nav']
        if last_peak_nav is None or last_peak_nav <= 0:
            return result
            
        gain_pct = (nav - last_peak_nav) / last_peak_nav

        # 未达到10%止盈线
        if gain_pct < 0.10:
            return result

        units = self.state['units']
        if units <= 0:
            return result

        # ≥10% → 卖出25%
        if 0.10 <= gain_pct < 0.20:
            sell_units = units * 0.25
            result['sell_units'] = sell_units
            result['note'] = f"止盈10%: 卖出25%份额({sell_units:.2f}份)"

        # ≥20% → 再卖25%
        elif 0.20 <= gain_pct < 0.30:
            sell_units = units * 0.25
            result['sell_units'] = sell_units
            result['note'] = f"止盈20%: 卖出25%份额({sell_units:.2f}份)"

        # ≥30% → 卖光盈利（本金不卖）
        elif gain_pct >= 0.30:
            position_value = units * nav
            total_cost = self.state['total_cost']
            profit = max(0, position_value - total_cost)

            if profit > 0:
                units_to_sell = profit / nav
                result['sell_units'] = units_to_sell
                result['note'] = f"止盈30%: 卖出盈利部分({units_to_sell:.2f}份)"

        return result

    # -------------------------------------------------------
    # 低估补仓逻辑
    # -------------------------------------------------------
    def _check_dip_buy(self, nav, cash_pool) -> Dict:
        """检查低估补仓条件"""
        result = {'buy_cash': 0.0, 'note': ''}
        
        last_peak_nav = self.state['last_peak_nav']
        if last_peak_nav is None or last_peak_nav <= 0:
            return result
            
        dip_pct = (nav - last_peak_nav) / last_peak_nav

        # 小跌不补
        if dip_pct > -0.05:
            return result

        # 计算盈利部分
        units = self.state['units']
        position_value = units * nav
        total_cost = self.state['total_cost']
        profit = max(0, position_value - total_cost)

        buy_amount = 0.0

        # -5% 低估：补盈利 40%
        if -0.10 < dip_pct <= -0.05:
            buy_amount = profit * 0.40
            result['note'] = f"低估5%: 补仓盈利40%"

        # -10% 低估：补盈利 70%
        elif -0.15 < dip_pct <= -0.10:
            buy_amount = profit * 0.70
            result['note'] = f"低估10%: 补仓盈利70%"

        # -15% 低估：盈利100% + 本金200
        else:  # dip <= -15%
            buy_amount = profit * 1.0 + min(200, cash_pool)
            result['note'] = f"低估15%: 补仓盈利100%+本金200"

        # 不允许买超过现金池
        buy_amount = min(buy_amount, cash_pool)

        if buy_amount > 0:
            result['buy_cash'] = buy_amount
            result['note'] += f"({buy_amount:.0f}元)"
        else:
            result['note'] = ''

        return result

    def get_stats(self) -> Dict[str, Any]:
        """获取策略统计"""
        return {
            'peak_nav': self.state.get('last_peak_nav', 0),
        }

    def get_name(self) -> str:
        return "主人策略V1"
