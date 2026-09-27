#!/usr/bin/env python3
"""Search APK-contained files for reverse-engineering leads."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path


DEFAULT_TERMS = (
    "JNI_OnLoad",
    "RegisterNatives",
    "girf_sqlite3_key",
    "girf_sync.db",
    "bedstone.db",
    "fLocationInfo",
    "key_name",
    "__internal_db_category_",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--term", action="append", dest="terms")
    parser.add_argument("--context", type=int, default=48)
    parser.add_argument("--max-hits", type=int, default=200)
    return parser.parse_args()


def visible(data: bytes) -> str:
    return "".join(chr(value) if 32 <= value < 127 else "." for value in data)


def main() -> int:
    args = parse_args()
    if not args.archive.is_file():
        print(f"not a file: {args.archive}", file=sys.stderr)
        return 2
    terms = args.terms or list(DEFAULT_TERMS)
    hits = 0
    with zipfile.ZipFile(args.archive) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            lower = info.filename.lower()
            if not lower.endswith((".dex", ".so", ".xml", ".json", ".properties", ".txt")):
                continue
            data = archive.read(info)
            for term in terms:
                for encoding, needle in (
                    ("utf-8", term.encode("utf-8")),
                    ("utf-16le", term.encode("utf-16le")),
                    ("utf-16be", term.encode("utf-16be")),
                ):
                    start = 0
                    while hits < args.max_hits:
                        offset = data.find(needle, start)
                        if offset < 0:
                            break
                        left = max(0, offset - args.context)
                        right = min(len(data), offset + len(needle) + args.context)
                        print(f"{info.filename}\t{offset:#x}\t{encoding}\t{term}")
                        print(f"  {visible(data[left:right])}")
                        hits += 1
                        start = offset + len(needle)
            if hits >= args.max_hits:
                return 0
    print(f"hits: {hits}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
