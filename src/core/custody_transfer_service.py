#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
场外基金转托管到场内（LOF）服务

当前功能：
- 记录转托管事件到 fund_custody_transfer 表
- 提供简单的查询接口，后续可用于持仓/快照计算
"""

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import List, Dict, Optional

from data.db_connector import execute_insert, execute_query


@dataclass
class FundCustodyTransfer:
    id: Optional[int]
    product_code: str
    from_channel: str
    to_channel: str
    transfer_date: str
    transfer_shares: Decimal
    note: str = ""


def add_fund_custody_transfer(
    product_code: str,
    transfer_shares: Decimal,
    transfer_date: Optional[date] = None,
    from_channel: str = "OTC",
    to_channel: str = "EXCHANGE",
    note: str = "",
) -> int:
    """
    新增一条转托管记录（场外 -> 场内）

    目前仅做记录，不直接改动持仓或交易流水。
    """
    if transfer_date is None:
        transfer_date = date.today()

    sql = """
        INSERT INTO fund_custody_transfer
        (product_code, from_channel, to_channel, transfer_date, transfer_shares, note)
        VALUES (%s, %s, %s, %s, %s, %s)
    """
    params = (
        product_code,
        from_channel,
        to_channel,
        transfer_date.strftime("%Y-%m-%d"),
        str(transfer_shares),
        note or None,
    )
    new_id = execute_insert(sql, params)
    return int(new_id) if new_id is not None else 0


def list_fund_custody_transfers(product_code: Optional[str] = None) -> List[Dict]:
    """
    按产品代码查询转托管记录（按日期正序）。
    """
    if product_code:
        sql = """
            SELECT id, product_code, from_channel, to_channel,
                   DATE_FORMAT(transfer_date, '%%Y-%%m-%%d') AS transfer_date,
                   transfer_shares, note, created_at, updated_at
            FROM fund_custody_transfer
            WHERE product_code = %s
            ORDER BY transfer_date, id
        """
        return execute_query(sql, (product_code,))
    else:
        sql = """
            SELECT id, product_code, from_channel, to_channel,
                   DATE_FORMAT(transfer_date, '%%Y-%%m-%%d') AS transfer_date,
                   transfer_shares, note, created_at, updated_at
            FROM fund_custody_transfer
            ORDER BY product_code, transfer_date, id
        """
        return execute_query(sql)


