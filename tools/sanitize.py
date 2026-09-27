#!/usr/bin/env python3
"""Scan a public tree for credentials and private operational data."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


INTERNAL_PATH = r"(?:/data/" + r"Sync/|" + r"/ro" + r"ot/|" + r"/ho" + r"me/[A-Za-z0-9_.-]+/)"
PATTERNS = (
    ("telegram-token", re.compile(r"\b\d{6,12}:[A-Za-z0-9_-]{25,}\b")),
    ("private-key", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("aws-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("internal-ipv4", re.compile(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|127\.0\.0\.1)\b")),
    ("internal-path", re.compile(INTERNAL_PATH)),
    ("private-uuid", re.compile(r"\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b", re.I)),
    ("coordinate-pair", re.compile(r"\b1[01]\d\.\d{5,}\b\s*[,/]\s*\b[2-5]\d\.\d{5,}\b")),
    ("private-host", re.compile(r"\b[a-z0-9.-]*workers\.[a-z0-9.-]+\b", re.I)),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--max-file-bytes", type=int, default=2_000_000)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    findings = 0
    for path in sorted(args.root.rglob("*")):
        if not path.is_file() or ".git" in path.parts or path.stat().st_size > args.max_file_bytes:
            continue
        data = path.read_bytes()
        if b"\x00" in data:
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            for label, pattern in PATTERNS:
                if pattern.search(line):
                    print(f"{label}\t{path}:{line_number}")
                    findings += 1
    print(f"findings: {findings}")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
