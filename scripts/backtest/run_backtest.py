"""Offline CSV backtest CLI. JSON goes to stdout; errors go to stderr."""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from core.backtest.engine import InputError, batch, load_nav, run


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-root", type=Path, default=Path("data"))
    parser.add_argument("--data")
    parser.add_argument("--strategy", default="pure_sip")
    parser.add_argument("--version", default="1")
    parser.add_argument("--params", default="{}")
    parser.add_argument("--batch", type=Path)
    args = parser.parse_args()
    try:
        if args.batch:
            if args.data:
                raise InputError("choose --data or --batch")
            # Config is also constrained to the chosen data root.
            config_path = args.batch.resolve()
            if not config_path.is_relative_to(args.data_root.resolve()) or config_path.suffix != ".json":
                raise InputError("batch config must be JSON inside data_root")
            config = json.loads(config_path.read_text(encoding="utf-8"))
            result = batch(config, args.data_root)
        else:
            if not args.data:
                raise InputError("--data is required")
            rows, digest = load_nav(Path(args.data), args.data_root)
            result = run(rows, digest, args.strategy, args.version, json.loads(args.params))
        print(json.dumps(result, sort_keys=True, separators=(",", ":"), allow_nan=False))
        return 0
    except (InputError, OSError, ValueError, TypeError, KeyError) as exc:
        print(json.dumps({"error": {"code": "INVALID_INPUT", "message": str(exc)}}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
