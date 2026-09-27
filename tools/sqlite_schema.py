#!/usr/bin/env python3
"""Inspect a decrypted SQLite database in read-only mode."""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path)
    parser.add_argument("--ddl", action="store_true")
    parser.add_argument("--counts", action="store_true")
    parser.add_argument("--integrity", action="store_true")
    parser.add_argument("--json", action="store_true")
    return parser.parse_args()


def quote(value: str) -> str:
    return '"' + value.replace('"', '""') + '"'


def inspect(path: Path, ddl: bool, counts: bool, integrity: bool) -> dict:
    connection = sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)
    connection.execute("PRAGMA query_only=ON")
    try:
        objects = []
        rows = connection.execute(
            "SELECT type, name, tbl_name, sql FROM sqlite_master "
            "WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name"
        ).fetchall()
        for object_type, name, table, object_ddl in rows:
            item = {"type": object_type, "name": name, "table": table}
            if ddl:
                item["ddl"] = object_ddl
            if object_type == "table":
                item["columns"] = [
                    {"cid": row[0], "name": row[1], "type": row[2], "notnull": row[3], "pk": row[5]}
                    for row in connection.execute(f"PRAGMA table_info({quote(name)})")
                ]
                if counts:
                    item["rows"] = connection.execute(
                        f"SELECT count(*) FROM {quote(name)}"
                    ).fetchone()[0]
            objects.append(item)
        result = {"database": str(path), "objects": objects}
        if integrity:
            result["integrity_check"] = [row[0] for row in connection.execute("PRAGMA integrity_check")]
        return result
    finally:
        connection.close()


def main() -> int:
    args = parse_args()
    if not args.database.is_file():
        print(f"not a file: {args.database}", file=sys.stderr)
        return 2
    try:
        result = inspect(args.database, args.ddl, args.counts, args.integrity)
    except sqlite3.DatabaseError as error:
        print(f"sqlite error: {error}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        for item in result["objects"]:
            suffix = f" rows={item['rows']}" if "rows" in item else ""
            print(f"{item['type']}\t{item['name']}{suffix}")
            for column in item.get("columns", []):
                print(f"  {column['name']}\t{column['type'] or 'ANY'}\tpk={column['pk']}")
            if args.ddl and item.get("ddl"):
                print(f"  DDL: {item['ddl']}")
        if args.integrity:
            print("integrity_check:", ", ".join(result["integrity_check"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
