#!/usr/bin/env python3
"""Recover printable ASCII strings from single-byte-XOR data."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--key", type=lambda value: int(value, 0))
    parser.add_argument("--min-length", type=int, default=6)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.binary.is_file():
        print(f"not a file: {args.binary}", file=sys.stderr)
        return 2
    data = args.binary.read_bytes()
    keys = [args.key] if args.key is not None else range(1, 256)
    for key in keys:
        decoded = bytes(value ^ key for value in data)
        for match in re.finditer(rb"[ -~]{%d,}" % args.min_length, decoded):
            print(f"{match.group().decode('ascii')}\tkey=0x{key:02x}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
