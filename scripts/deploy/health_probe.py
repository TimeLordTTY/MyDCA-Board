#!/usr/bin/env python3
"""Validate a local curl response only; never opens a network connection."""
import argparse
import json
from pathlib import Path


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON key")
        result[key] = value
    return result


def healthy(code, body):
    if code != "200" or len(body) > 65536:
        return False
    try:
        def invalid_constant(_):
            raise ValueError("non-JSON constant")
        value = json.loads(body, object_pairs_hook=unique_object,
                           parse_constant=invalid_constant)
        return isinstance(value, dict) and value.get("status") == "UP" and "error" not in value
    except (ValueError, UnicodeError):
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--http-code", required=True)
    parser.add_argument("--file", type=Path, required=True)
    args = parser.parse_args()
    try:
        with args.file.open("rb") as response:
            body = response.read(65537)
        return 0 if healthy(args.http_code, body) else 1
    except OSError:
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
