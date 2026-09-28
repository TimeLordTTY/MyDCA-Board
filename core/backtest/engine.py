"""Deterministic, offline NAV backtests. No account or broker integration."""
from __future__ import annotations

import csv
import hashlib
import json
import math
from datetime import date
from pathlib import Path

VERSION = "1.0.0"
STRATEGIES = {("pure_sip", "1"), ("ma_enhanced", "1"), ("profit_recycle", "1"), ("profit_recycle", "2")}
MAX_COMBINATIONS = 64
MAX_ROWS = 100000


class InputError(ValueError):
    pass


def _number(value, name, *, minimum=0):
    if isinstance(value, bool):
        raise InputError(f"{name} must be a finite number")
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise InputError(f"{name} must be a finite number") from exc
    if not math.isfinite(result) or result < minimum:
        raise InputError(f"{name} must be finite and >= {minimum}")
    return result


def load_nav(path: Path, root: Path):
    root = root.resolve()
    path = (root / path).resolve()
    if not path.is_relative_to(root) or path.suffix.lower() != ".csv" or not path.is_file():
        raise InputError("data must be an existing CSV inside data_root")
    raw = path.read_bytes()
    if len(raw) > 20_000_000:
        raise InputError("data file exceeds 20 MB")
    try:
        reader = csv.DictReader(raw.decode("utf-8-sig").splitlines())
        if not reader.fieldnames or not {"date", "nav"}.issubset(reader.fieldnames):
            raise InputError("CSV requires date,nav columns")
        rows = []
        previous = None
        for row in reader:
            day = date.fromisoformat(row["date"])
            if previous is not None and day <= previous:
                raise InputError("dates must be strictly increasing and unique")
            nav = _number(row["nav"], "nav", minimum=0.000000001)
            rows.append((day, nav))
            previous = day
            if len(rows) > MAX_ROWS:
                raise InputError("too many data rows")
    except (UnicodeError, csv.Error, TypeError, ValueError) as exc:
        if isinstance(exc, InputError):
            raise
        raise InputError("invalid CSV date or NAV") from exc
    if not rows:
        raise InputError("empty NAV data")
    return rows, hashlib.sha256(raw).hexdigest()


def _params(strategy, params):
    if not isinstance(params, dict):
        raise InputError("params must be an object")
    allowed = {"contribution", "interval_days"}
    if strategy == "ma_enhanced":
        allowed |= {"ma_window", "dip_multiplier"}
    if strategy == "profit_recycle":
        allowed |= {"profit_threshold", "sell_fraction"}
    if set(params) - allowed:
        raise InputError("unknown strategy parameter")
    contribution = _number(params.get("contribution", 100), "contribution", minimum=0.000000001)
    interval = params.get("interval_days", 30)
    if type(interval) is not int or not 1 <= interval <= 365:
        raise InputError("interval_days must be an integer from 1 to 365")
    result = {"contribution": contribution, "interval_days": interval}
    if strategy == "ma_enhanced":
        window = params.get("ma_window", 20)
        if type(window) is not int or not 2 <= window <= 365:
            raise InputError("ma_window must be an integer from 2 to 365")
        result.update(ma_window=window, dip_multiplier=_number(params.get("dip_multiplier", 2), "dip_multiplier", minimum=1))
    if strategy == "profit_recycle":
        threshold = _number(params.get("profit_threshold", 0.2), "profit_threshold", minimum=0.000000001)
        fraction = _number(params.get("sell_fraction", 0.25), "sell_fraction", minimum=0.000000001)
        if fraction > 1:
            raise InputError("sell_fraction must be <= 1")
        result.update(profit_threshold=threshold, sell_fraction=fraction)
    return result


def run(rows, digest, strategy, version="1", params=None):
    version = str(version)
    if (strategy, version) not in STRATEGIES:
        raise InputError("unknown strategy or version")
    p = _params(strategy, params or {})
    cash = shares = invested = peak = drawdown = 0.0
    trades = buys = sells = 0
    first = rows[0][0]
    next_day = first
    past = []
    for day, nav in rows:
        # Only prior observations may influence today's decision.
        average = sum(past[-p.get("ma_window", 1):]) / min(len(past), p.get("ma_window", 1)) if past else None
        if day >= next_day:
            amount = p["contribution"]
            invested += amount
            cash += amount
            if strategy == "ma_enhanced" and average is not None and nav < average:
                amount = min(cash, amount * p["dip_multiplier"])
            shares += amount / nav
            cash -= amount
            buys += 1
            trades += 1
            next_day = date.fromordinal(day.toordinal() + p["interval_days"])
        if strategy == "profit_recycle" and shares > 0:
            value = shares * nav + cash
            if value > invested * (1 + p["profit_threshold"]):
                fraction = p["sell_fraction"] * (0.5 if version == "2" else 1)
                sold = shares * fraction
                shares -= sold
                cash += sold * nav
                sells += 1
                trades += 1
        value = shares * nav + cash
        # Drawdown uses account value; contributions can lift peaks, so this is
        # descriptive only and should not be interpreted as time-weighted risk.
        peak = max(peak, value)
        drawdown = min(drawdown, value / peak - 1 if peak else 0)
        past.append(nav)
    end = rows[-1][0]
    final = shares * rows[-1][1] + cash
    total = final / invested - 1
    days = (end - first).days
    annual = (1 + total) ** (365 / days) - 1 if days and final > 0 else None
    identity = {"engine_version": VERSION, "data_sha256": digest, "start": first.isoformat(), "end": end.isoformat(), "strategy": strategy, "strategy_version": version, "params": p}
    run_id = hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()[:24]
    return {"schema_version": "1", "run_id": run_id, "provenance": identity, "data_range": {"start": first.isoformat(), "end": end.isoformat(), "rows": len(rows)}, "strategy": {"name": strategy, "version": version, "params": p}, "metrics": {"invested": round(invested, 8), "final_assets": round(final, 8), "total_return": round(total, 8), "annualized_return": round(annual, 8) if annual is not None else None, "max_drawdown": round(drawdown, 8), "trade_count": trades, "cash": round(cash, 8), "holdings": round(shares, 8)}, "events": {"buys": buys, "sells": sells}, "warnings": ["Historical simulation only; no live trading instruction."] + (["Annualized return unavailable for a single date."] if annual is None else [])}


def batch(config, root):
    if not isinstance(config, dict) or set(config) != {"data", "strategies"}:
        raise InputError("config requires only data and strategies")
    rows, digest = load_nav(Path(config["data"]), root)
    specs = config["strategies"]
    if not isinstance(specs, list) or not specs:
        raise InputError("strategies must be a nonempty list")
    jobs = []
    for spec in specs:
        if not isinstance(spec, dict) or set(spec) - {"name", "version", "parameters"}:
            raise InputError("invalid strategy specification")
        name, version = spec.get("name"), str(spec.get("version", "1"))
        variants = spec.get("parameters", [{}])
        if not isinstance(variants, list) or not variants:
            raise InputError("parameters must be a nonempty list")
        for p in variants:
            jobs.append((name, version, p))
            if len(jobs) > MAX_COMBINATIONS:
                raise InputError("batch exceeds 64 combinations")
    results = []
    for name, version, p in jobs:
        result = run(rows, digest, name, version, p)
        baseline = run(rows, digest, "pure_sip", "1", {k: result["strategy"]["params"][k] for k in ("contribution", "interval_days")})
        a, b = result["metrics"], baseline["metrics"]
        result["baseline"] = {"strategy": "pure_sip", "run_id": baseline["run_id"], "final_assets_delta": round(a["final_assets"] - b["final_assets"], 8), "annualized_return_delta": round(a["annualized_return"] - b["annualized_return"], 8) if a["annualized_return"] is not None and b["annualized_return"] is not None else None, "max_drawdown_delta": round(a["max_drawdown"] - b["max_drawdown"], 8)}
        results.append(result)
    return {"schema_version": "1", "data_sha256": digest, "results": results}
