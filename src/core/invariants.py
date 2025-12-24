#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
一致性校验器（P0-4）

对每个资产、每个日期/最新快照做自洽性校验，输出可读错误信息。

设计原则：
- 校验失败时不要静默吞掉，必须明确报错原因（哪条流水、哪天、哪个字段）
- 所有校验必须可复现，不依赖外部状态
- 校验结果可用于日志与页面提示
"""
from decimal import Decimal
from typing import List, Dict, Optional, Tuple
from datetime import datetime
import logging

from utils.decimal_utils import to_dec, q_money, q_shares, q_nav, is_zero, is_negative
from core.holdings_calculator import HoldingsCalculator
from data.db_connector import execute_query, execute_one

logger = logging.getLogger(__name__)


class InvariantViolation(Exception):
    """不变量违反异常"""
    def __init__(self, message: str, product_code: str = None, date: str = None, field: str = None):
        self.message = message
        self.product_code = product_code
        self.date = date
        self.field = field
        super().__init__(self.message)
    
    def __str__(self):
        parts = [self.message]
        if self.product_code:
            parts.append(f"产品: {self.product_code}")
        if self.date:
            parts.append(f"日期: {self.date}")
        if self.field:
            parts.append(f"字段: {self.field}")
        return " | ".join(parts)


class InvariantChecker:
    """一致性校验器"""
    
    def __init__(self, threshold: Decimal = None):
        """
        初始化校验器
        
        :param threshold: 舍入误差阈值（默认 Decimal('0.01')，用于金额比较）
        """
        if threshold is None:
            threshold = Decimal('0.01')
        self.threshold = threshold
    
    def check_holdings(self, product_code: str, asof_date: str) -> List[InvariantViolation]:
        """
        检查单个产品的持仓数据一致性
        
        :param product_code: 产品代码
        :param asof_date: 截止日期
        :return: 违反的不变量列表
        """
        violations = []
        calc = HoldingsCalculator()
        
        # 获取持仓数据
        holdings_data = calc.get_all_holdings_data_as_of(asof_date)
        if product_code not in holdings_data:
            return violations  # 无持仓，无需校验
        
        h = holdings_data[product_code]
        shares = to_dec(h.get('shares', 0))
        cost = to_dec(h.get('cost', 0))
        
        # Invariant 1: shares >= 0
        if is_negative(shares):
            violations.append(InvariantViolation(
                f"份额不能为负: {shares}",
                product_code=product_code,
                date=asof_date,
                field='shares'
            ))
        
        # Invariant 2: cost >= 0
        if is_negative(cost):
            violations.append(InvariantViolation(
                f"成本不能为负: {cost}",
                product_code=product_code,
                date=asof_date,
                field='cost'
            ))
        
        return violations
    
    def check_snapshot(self, product_code: str, fetch_date: str) -> List[InvariantViolation]:
        """
        检查单个产品的快照数据一致性
        
        :param product_code: 产品代码
        :param fetch_date: 快照日期
        :return: 违反的不变量列表
        """
        violations = []
        
        # 获取快照数据
        sql = """
            SELECT fetch_date, product_code, shares, nav, `value`, cost,
                   unrealized_pnl, pnl_day, data_status
            FROM daily_snapshot
            WHERE product_code = %s AND fetch_date = %s
        """
        snapshot = execute_one(sql, (product_code, fetch_date))
        
        if not snapshot:
            return violations  # 无快照，无需校验
        
        shares = to_dec(snapshot.get('shares', 0))
        nav = to_dec(snapshot.get('nav', 0))
        value = to_dec(snapshot.get('value', 0))
        cost = to_dec(snapshot.get('cost', 0))
        unrealized_pnl = to_dec(snapshot.get('unrealized_pnl', 0))
        pnl_day = to_dec(snapshot.get('pnl_day', 0))
        data_status = snapshot.get('data_status', 'ok')
        
        # Invariant 1: shares >= 0
        if is_negative(shares):
            violations.append(InvariantViolation(
                f"快照份额不能为负: {shares}",
                product_code=product_code,
                date=fetch_date,
                field='shares'
            ))
        
        # Invariant 2: cost >= 0
        if is_negative(cost):
            violations.append(InvariantViolation(
                f"快照成本不能为负: {cost}",
                product_code=product_code,
                date=fetch_date,
                field='cost'
            ))
        
        # Invariant 3: 若 shares == 0，则 market_value == 0 且 cost == 0
        if is_zero(shares, threshold=Decimal('0.000001')):
            if not is_zero(value, threshold=self.threshold):
                violations.append(InvariantViolation(
                    f"份额为0时市值必须为0: shares={shares}, value={value}",
                    product_code=product_code,
                    date=fetch_date,
                    field='value'
                ))
            if not is_zero(cost, threshold=self.threshold):
                violations.append(InvariantViolation(
                    f"份额为0时成本必须为0: shares={shares}, cost={cost}",
                    product_code=product_code,
                    date=fetch_date,
                    field='cost'
                ))
        
        # Invariant 4: market_value == q_money(shares * nav_or_price)（阈值<=0.01）
        expected_value = q_money(shares * nav)
        value_diff = abs(value - expected_value)
        if value_diff > self.threshold:
            violations.append(InvariantViolation(
                f"市值计算不一致: value={value}, 期望={expected_value}, 差异={value_diff}",
                product_code=product_code,
                date=fetch_date,
                field='value'
            ))
        
        # Invariant 5: unrealized_pnl = value - cost（允许舍入误差）
        expected_unrealized = q_money(value - cost)
        unrealized_diff = abs(unrealized_pnl - expected_unrealized)
        if unrealized_diff > self.threshold:
            violations.append(InvariantViolation(
                f"浮动盈亏计算不一致: unrealized_pnl={unrealized_pnl}, 期望={expected_unrealized}, 差异={unrealized_diff}",
                product_code=product_code,
                date=fetch_date,
                field='unrealized_pnl'
            ))
        
        # Invariant 6: pnl_day 计算可复算（与 prev_day 数据一致）
        if data_status != 'missing':
            prev_snapshot = self._get_prev_snapshot(product_code, fetch_date)
            if prev_snapshot:
                prev_shares = to_dec(prev_snapshot.get('shares', 0))
                prev_nav = to_dec(prev_snapshot.get('nav', 0))
                expected_pnl_day = q_money(prev_shares * (nav - prev_nav))
                pnl_diff = abs(pnl_day - expected_pnl_day)
                if pnl_diff > self.threshold:
                    violations.append(InvariantViolation(
                        f"日变动计算不一致: pnl_day={pnl_day}, 期望={expected_pnl_day}, 差异={pnl_diff}",
                        product_code=product_code,
                        date=fetch_date,
                        field='pnl_day'
                    ))
            elif data_status == 'ok':
                # 有 data_status=ok 但没有上一交易日快照，应该标记为 missing
                violations.append(InvariantViolation(
                    f"data_status=ok 但缺少上一交易日快照，应标记为 missing",
                    product_code=product_code,
                    date=fetch_date,
                    field='data_status'
                ))
        
        return violations
    
    def check_cash_closure(self, product_code: str, fetch_date: str) -> List[InvariantViolation]:
        """
        检查现金闭环（Invariant 6）
        
        公式：total_value_today ≈ total_value_yesterday + net_cashflow_today + pnl_day
        
        :param product_code: 产品代码
        :param fetch_date: 快照日期
        :return: 违反的不变量列表
        """
        violations = []
        
        # 获取今日快照
        sql = """
            SELECT fetch_date, total_value, pnl_day, principal_total, total_redemption
            FROM daily_snapshot
            WHERE product_code = %s AND fetch_date = %s
        """
        today_snapshot = execute_one(sql, (product_code, fetch_date))
        
        if not today_snapshot:
            return violations
        
        # 获取昨日快照
        prev_snapshot = self._get_prev_snapshot(product_code, fetch_date)
        if not prev_snapshot:
            return violations  # 无昨日快照，无法校验
        
        today_total_value = to_dec(today_snapshot.get('total_value', 0))
        prev_total_value = to_dec(prev_snapshot.get('total_value', 0))
        pnl_day = to_dec(today_snapshot.get('pnl_day', 0))
        
        # 计算当日净现金流（从交易流水汇总）
        net_cashflow = self._calc_net_cashflow(product_code, fetch_date)
        
        # 校验公式
        expected_total_value = prev_total_value + net_cashflow + pnl_day
        diff = abs(today_total_value - expected_total_value)
        
        if diff > self.threshold:
            violations.append(InvariantViolation(
                f"现金闭环不一致: total_value={today_total_value}, 期望={expected_total_value}, "
                f"差异={diff}, net_cashflow={net_cashflow}, pnl_day={pnl_day}",
                product_code=product_code,
                date=fetch_date,
                field='total_value'
            ))
        
        return violations
    
    def check_idempotency(self, product_code: str, fetch_date: str) -> List[InvariantViolation]:
        """
        检查幂等性（Invariant 7）
        
        同一日期同一资产快照幂等：重复跑快照生成不会产生不同结果
        
        :param product_code: 产品代码
        :param fetch_date: 快照日期
        :return: 违反的不变量列表（通常为空，除非检测到不一致）
        """
        violations = []
        
        # 获取该日期的所有快照记录（按 fetched_at 排序）
        sql = """
            SELECT fetch_date, product_code, shares, nav, `value`, cost,
                   total_value, fetched_at
            FROM daily_snapshot
            WHERE product_code = %s AND fetch_date = %s
            ORDER BY fetched_at DESC
        """
        snapshots = execute_query(sql, (product_code, fetch_date))
        
        if len(snapshots) <= 1:
            return violations  # 只有一条记录，无法比较
        
        # 比较最新两条记录的关键字段
        latest = snapshots[0]
        second_latest = snapshots[1]
        
        key_fields = ['shares', 'nav', 'value', 'cost', 'total_value']
        for field in key_fields:
            latest_val = to_dec(latest.get(field, 0))
            second_val = to_dec(second_latest.get(field, 0))
            diff = abs(latest_val - second_val)
            
            if diff > self.threshold:
                violations.append(InvariantViolation(
                    f"幂等性违反: 字段 {field} 在两次生成中不一致, "
                    f"最新={latest_val}, 上次={second_val}, 差异={diff}",
                    product_code=product_code,
                    date=fetch_date,
                    field=field
                ))
        
        return violations
    
    def check_all_products(self, asof_date: str) -> List[InvariantViolation]:
        """
        检查所有产品的持仓一致性
        
        :param asof_date: 截止日期
        :return: 所有违反的不变量列表
        """
        violations = []
        calc = HoldingsCalculator()
        holdings_data = calc.get_all_holdings_data_as_of(asof_date)
        
        for product_code in holdings_data:
            product_violations = self.check_holdings(product_code, asof_date)
            violations.extend(product_violations)
        
        return violations
    
    def check_all_snapshots(self, fetch_date: str) -> List[InvariantViolation]:
        """
        检查指定日期的所有快照一致性
        
        :param fetch_date: 快照日期
        :return: 所有违反的不变量列表
        """
        violations = []
        
        sql = """
            SELECT DISTINCT product_code
            FROM daily_snapshot
            WHERE fetch_date = %s
        """
        products = execute_query(sql, (fetch_date,))
        
        for row in products:
            product_code = row.get('product_code')
            product_violations = self.check_snapshot(product_code, fetch_date)
            violations.extend(product_violations)
        
        return violations
    
    def _get_prev_snapshot(self, product_code: str, fetch_date: str) -> Optional[Dict]:
        """获取上一交易日快照"""
        sql = """
            SELECT shares, nav, `value`, total_value, pnl_day
            FROM daily_snapshot
            WHERE product_code = %s AND fetch_date < %s
            ORDER BY fetch_date DESC
            LIMIT 1
        """
        return execute_one(sql, (product_code, fetch_date))
    
    def _calc_net_cashflow(self, product_code: str, fetch_date: str) -> Decimal:
        """
        计算当日净现金流（从交易流水汇总）
        
        :param product_code: 产品代码
        :param fetch_date: 快照日期
        :return: 净现金流（正数表示流入，负数表示流出）
        """
        sql = """
            SELECT action, amount, fee
            FROM transactions
            WHERE product_code = %s AND `date` = %s
        """
        transactions = execute_query(sql, (product_code, fetch_date))
        
        net_cashflow = Decimal('0')
        for tx in transactions:
            action = (tx.get('action') or '').lower()
            amount = to_dec(tx.get('amount', 0))
            fee = to_dec(tx.get('fee', 0))
            
            if action in ('buy_debit', 'buy'):
                # 买入：资金流出（负）
                net_cashflow -= amount
            elif action in ('sell_confirm', 'sell'):
                # 卖出：资金流入（正，到账净额）
                net_cashflow += amount
            # buy_confirm, dividend 不涉及现金流动（已在 buy_debit 或分红时处理）
        
        return net_cashflow


def validate_all(asof_date: str = None) -> Tuple[bool, List[InvariantViolation]]:
    """
    校验所有不变量（便捷函数）
    
    :param asof_date: 截止日期（默认今天）
    :return: (是否通过, 违反的不变量列表)
    """
    if asof_date is None:
        asof_date = datetime.now().strftime('%Y-%m-%d')
    
    checker = InvariantChecker()
    violations = []
    
    # 检查持仓
    holdings_violations = checker.check_all_products(asof_date)
    violations.extend(holdings_violations)
    
    # 检查快照
    snapshot_violations = checker.check_all_snapshots(asof_date)
    violations.extend(snapshot_violations)
    
    # 检查现金闭环（仅当日快照）
    for v in snapshot_violations:
        if hasattr(v, 'product_code') and v.product_code:
            cash_violations = checker.check_cash_closure(v.product_code, asof_date)
            violations.extend(cash_violations)
    
    success = len(violations) == 0
    
    if not success:
        logger.error(f"一致性校验失败: 发现 {len(violations)} 个违反")
        for v in violations:
            logger.error(f"  - {v}")
    else:
        logger.info(f"一致性校验通过: {asof_date}")
    
    return success, violations


if __name__ == "__main__":
    # 命令行测试
    import sys
    logging.basicConfig(level=logging.INFO)
    
    date = sys.argv[1] if len(sys.argv) > 1 else None
    success, violations = validate_all(date)
    
    if not success:
        print(f"✗ 校验失败: 发现 {len(violations)} 个违反")
        for v in violations:
            print(f"  - {v}")
        sys.exit(1)
    else:
        print("✓ 校验通过")
        sys.exit(0)

