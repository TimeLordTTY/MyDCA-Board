# -*- coding: utf-8 -*-
"""
东方财富 / akshare 常用网络异常的重试封装（RemoteDisconnected、连接重置等）。
供 etf_collector、fund_collector 等脚本共用，行为可通过 ETF_HIST_MAX_RETRIES 调节。
"""
from __future__ import annotations

import http.client
import os
import random
import time
import logging
from typing import Callable, TypeVar

import requests
from urllib3.exceptions import ProtocolError, MaxRetryError

logger = logging.getLogger(__name__)

T = TypeVar("T")


def _max_retries_default() -> int:
    return max(1, int(os.environ.get("ETF_HIST_MAX_RETRIES", "5")))


def is_retryable_network_error(exc: BaseException) -> bool:
    """是否为可重试的网络层异常（含异常链上的 cause/context）。"""
    retryable_types = (
        requests.exceptions.ConnectionError,
        requests.exceptions.Timeout,
        requests.exceptions.ChunkedEncodingError,
        requests.exceptions.SSLError,
        ProtocolError,
        MaxRetryError,
        OSError,
        http.client.RemoteDisconnected,
        BrokenPipeError,
        ConnectionResetError,
    )

    def _one(e: BaseException) -> bool:
        return isinstance(e, retryable_types)

    cur: BaseException | None = exc
    seen: set[int] = set()
    while cur is not None and id(cur) not in seen:
        seen.add(id(cur))
        if _one(cur):
            return True
        cur = cur.__cause__ or cur.__context__
    return False


def retry_ak_call(
    label: str,
    fn: Callable[..., T],
    *args,
    max_retries: int | None = None,
    **kwargs,
) -> T:
    """
    对 akshare 等返回 DataFrame 的调用做指数退避重试。
    非网络类异常立即抛出，不重试。
    """
    retries = max_retries if max_retries is not None else _max_retries_default()
    last_exc: BaseException | None = None
    for attempt in range(retries):
        try:
            return fn(*args, **kwargs)
        except Exception as e:
            last_exc = e
            if not is_retryable_network_error(e):
                raise
            if attempt >= retries - 1:
                break
            # 略拉长首次等待，减轻东方财富限连
            wait = min(45.0, (2**attempt) * 0.6 + random.uniform(0.05, 0.55))
            logger.warning(
                "%s 第 %s/%s 次失败: %s，%.1fs 后重试",
                label,
                attempt + 1,
                retries,
                e,
                wait,
            )
            time.sleep(wait)
    assert last_exc is not None
    raise last_exc
