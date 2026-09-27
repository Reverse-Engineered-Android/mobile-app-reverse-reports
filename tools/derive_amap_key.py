#!/usr/bin/env python3
"""Derive the AMap bedstone AES-128 key from a private ASCII passphrase."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--passphrase-env", default="AMAP_BEDSTONE_PASSPHRASE")
    parser.add_argument("--fingerprint", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    value = os.environ.get(args.passphrase_env)
    if not value:
        print(f"missing environment variable: {args.passphrase_env}", file=sys.stderr)
        return 2
    raw = value.encode("ascii")
    if len(raw) < 16:
        print("passphrase must contain at least 16 ASCII bytes", file=sys.stderr)
        return 2
    key = raw[:16]
    output = {"method": "ascii-prefix-16", "key_length": len(key)}
    if args.fingerprint:
        output["key_sha256"] = hashlib.sha256(key).hexdigest()
    else:
        output["key_hex"] = key.hex()
    print(json.dumps(output, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
