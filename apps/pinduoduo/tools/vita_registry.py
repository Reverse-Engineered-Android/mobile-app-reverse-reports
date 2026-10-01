#!/usr/bin/env python3
"""Decode Pinduoduo Vita component-framework metadata without running the app.

Handles the four on-disk surfaces of the Vita (Volantis) dynamic-component
framework:

  * ``files/.newLocker/*.vlock``  -- file locks whose *name* is
    ``MD5(component_id)`` (``-patch`` suffix = patch lock, trailing
    ``<version>`` = version lock).  Enumerating these yields the component
    registry, including components that are registered but never downloaded.
  * ``files/.vita/<cid>/<ver>/*.md5checker`` -- JSON ``{length, md5}`` table
    covering every packaged file of the component.
  * ``files/.vita/<cid>/<ver>/*.manifest`` -- ``PDD_MANIFEST`` keep-list.
  * ``files/mmkv/vita_local_comp_v2`` -- MMKV store of ``LocalComponentInfo``
    JSON records, i.e. what is actually installed.

MMKV framing here is a 4-byte little-endian ``actualSize``, a 4-byte CRC32,
then one 7-byte item header before the first varint-encoded key.  Both the
header offset and the varint layout are detected empirically (by scoring how
many parsed keys look like component ids) rather than hard-coded, because the
exact header width differs between stores.

Usage:
  vita_registry.py locks   <newLocker-dir> [candidate-id-file ...]
  vita_registry.py mmkv    <mmkv-store-file> [--offset N]
  vita_registry.py verify  <md5checker-dir> <hashes-file> [--base DIR]

``candidate-id-file`` supplies extra strings (component ids, library names,
DEX strings) used to invert the ``.vlock`` names back to component ids.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import zlib
from pathlib import Path

ID_RE = re.compile(rb"^[A-Za-z0-9._\-/$:+]{3,200}$")
SPECIAL_LOCKS = {
    "mmkv.vlock",
    "gc.vlock",
    "vita_database.vlock",
    "comp_meta_info_v3.vlock",
    "installed_comp_record.vlock",
}


def read_varint(buf: bytes, index: int) -> tuple[int, int, int]:
    value = 0
    shift = 0
    used = 0
    while index < len(buf):
        byte = buf[index]
        index += 1
        used += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, index, used
        shift += 7
    raise ValueError("truncated varint")


def parse_mmkv_at(buf: bytes, offset: int) -> list[tuple[str, bytes]]:
    items: list[tuple[str, bytes]] = []
    index = offset
    while index < len(buf) - 1:
        key_len, index, _ = read_varint(buf, index)
        if key_len <= 0 or key_len > 600 or index + key_len > len(buf):
            break
        key = buf[index:index + key_len].decode("utf-8", "replace")
        index += key_len
        val_len, index, _ = read_varint(buf, index)
        if val_len < 0 or index + val_len > len(buf):
            break
        raw = buf[index:index + val_len]
        index += val_len
        value = raw
        # MMKV writes each value behind its own length prefix; strip it when
        # the inner length accounts for the whole payload.
        try:
            inner, _, inner_len = read_varint(raw, 0)
            if inner == len(raw) - inner_len:
                value = raw[inner_len:]
        except ValueError:
            pass
        items.append((key, value))
    return items


def detect_offset(buf: bytes, limit: int = 24) -> int:
    """Pick the header width whose parse yields the most id-looking keys."""
    best = (0, 7)
    for offset in range(limit):
        items = parse_mmkv_at(buf, offset)
        score = sum(1 for key, _ in items if ID_RE.match(key.encode()))
        if score > best[0]:
            best = (score, offset)
    return best[1]


def dedupe(items: list[tuple[str, bytes]]) -> dict[str, bytes]:
    """MMKV appends updates; the last write for a key wins."""
    out: dict[str, bytes] = {}
    for key, value in items:
        out[key] = value
    return out


def collect_ids(paths: list[Path]) -> set[str]:
    """Harvest candidate component ids from JSON files and DEX/binary blobs."""
    pool: set[str] = set()

    def walk(node: object) -> None:
        if isinstance(node, str):
            pool.add(node.rstrip("/"))
        elif isinstance(node, dict):
            for key, value in node.items():
                walk(key)
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    for path in paths:
        data = path.read_bytes()
        try:
            walk(json.loads(data.decode("utf-8")))
            continue
        except Exception:
            pass
        # DEX/binary: split printable runs on separators so that a run like
        # `com.x.y","epoch":"1"` yields `com.x.y` rather than one long blob.
        for match in re.finditer(rb"[\x20-\x7e]{4,400}", data):
            for token in re.split(rb"[\x00-\x20\x7f\"\'(),;:{}<>\[\]|\\]", match.group()):
                if not token:
                    continue
                text = token.decode("ascii", "ignore").rstrip("/")
                if text.startswith(("com.", "almighty", "android.")) and len(text) < 200:
                    pool.add(text)
    return pool


def cmd_locks(args: argparse.Namespace) -> int:
    pool = collect_ids([Path(p) for p in args.ids])
    md5_to_id = {hashlib.md5(c.encode()).hexdigest().upper(): c for c in pool}
    names = sorted(p.name for p in Path(args.dir).iterdir() if p.name.endswith(".vlock"))
    resolved: dict[str, str] = {}
    unresolved: list[str] = []
    versioned: list[tuple[str, str]] = []
    for name in names:
        if name in SPECIAL_LOCKS:
            continue
        stem = name[: -len(".vlock")]
        core = stem[: -len("-patch")] if stem.endswith("-patch") else stem
        if len(core) == 32:
            if core in md5_to_id:
                resolved[name] = md5_to_id[core]
            else:
                unresolved.append(name)
        elif len(core) > 32 and core[:32] in md5_to_id:
            resolved[name] = md5_to_id[core[:32]]
            versioned.append((name, core[32:]))
        else:
            unresolved.append(name)
    components = {resolved[n] for n in resolved}
    print(f"vlock files        : {len(names)}")
    print(f"special locks      : {len(SPECIAL_LOCKS & set(names))}")
    print(f"version-suffixed   : {len(versioned)}")
    print(f"resolved ids       : {len(components)}")
    print(f"unresolved names   : {len(unresolved)}")
    for name in unresolved:
        print(f"  ? {name}")
    for name, version in versioned:
        print(f"  {name}  -> version {version}")
    for cid in sorted(components):
        print(f"  {cid}")
    return 0


def cmd_mmkv(args: argparse.Namespace) -> int:
    buf = Path(args.store).read_bytes()
    if len(buf) < 8:
        print("store too small", file=sys.stderr)
        return 1
    declared, crc = struct.unpack("<II", buf[:8])
    offset = args.offset if args.offset is not None else detect_offset(buf)
    items = dedupe(parse_mmkv_at(buf, offset))
    real_crc = zlib.crc32(buf[8:declared]) & 0xFFFFFFFF if 8 <= declared <= len(buf) else None
    print(f"declared_size={declared} stored_crc=0x{crc:08x} recomputed_crc="
          f"{'0x%08x' % real_crc if real_crc is not None else 'n/a'} offset={offset}")
    print(f"entries={len(items)}")
    for key, value in items.items():
        text = value.decode("utf-8", "replace")
        try:
            print(json.dumps(json.loads(text), ensure_ascii=False))
        except Exception:
            print(f"{key}\t{text[:400]!r}")
    return 0


def load_hashes(paths: list[Path]) -> dict[str, str]:
    """Read `md5sum`-style output from one or more files."""
    hashes: dict[str, str] = {}
    for path in paths:
        for line in path.read_text().splitlines():
            line = line.strip()
            if line:
                digest, _, name = line.partition("  ")
                hashes[name.lstrip("./")] = digest
    return hashes


def resolve(hashes: dict[str, str], cid: str, rel: str) -> str | None:
    """Find the hash of `rel` inside component `cid`, else by basename.

    The same payload can live in either `files/.vita/<cid>/<ver>/` or
    `files/dynamic_so/<lib>_<epoch>_<md5>/`, so both layouts are searched
    before falling back to a unique basename match.
    """
    for path, digest in hashes.items():
        if path.startswith(cid + "/") and path.endswith("/" + rel):
            return digest
    lib = rel.split("/")[-1]
    hits = {digest for path, digest in hashes.items()
            if path.endswith("/" + lib) or path == lib}
    return hits.pop() if len(hits) == 1 else None


def cmd_verify(args: argparse.Namespace) -> int:
    hashes = load_hashes([Path(p) for p in args.hashes])
    ok = diff = missing = 0
    for checker in sorted(Path(args.checker_dir).glob("*.md5checker")):
        payload = json.loads(checker.read_text())
        cid = payload.get("component_id", "")
        for rel, meta in payload.get("md5_list", {}).items():
            hit = resolve(hashes, cid, rel)
            if hit is None:
                print(f"MISSING\t{cid}\t{rel}")
                missing += 1
            elif hit == meta.get("md5"):
                ok += 1
            else:
                print(f"DIFF\t{cid}\t{rel}\tdeclared={meta.get('length')}")
                diff += 1
    print(f"ok={ok} diff={diff} missing={missing}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_locks = sub.add_parser("locks", help="invert .vlock names to component ids")
    p_locks.add_argument("dir")
    p_locks.add_argument("ids", nargs="*", default=[])
    p_locks.set_defaults(func=cmd_locks)

    p_mmkv = sub.add_parser("mmkv", help="dump an MMKV store")
    p_mmkv.add_argument("store")
    p_mmkv.add_argument("--offset", type=int, default=None)
    p_mmkv.set_defaults(func=cmd_mmkv)

    p_ver = sub.add_parser("verify", help="verify md5checker entries against real hashes")
    p_ver.add_argument("checker_dir")
    p_ver.add_argument("hashes", nargs="+")
    p_ver.set_defaults(func=cmd_verify)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
