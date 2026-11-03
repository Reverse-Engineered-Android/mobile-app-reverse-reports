#!/usr/bin/env python3
"""Recover the UPX-packed YJB native libraries without a runtime.

The four protected ARM64 libraries in ``lib/arm64-v8a`` are genuine UPX
containers whose tail ``b_info`` block is followed by a real (but truncated)
section header table.  Because that table sits after the UPX trailer, the tail
block no longer touches EOF and ``upx -d`` refuses the file with
``NotPackedException``.  Truncating the file so the trailer block ends exactly
at EOF is enough for the stock unpacker to accept it.

This script only truncates; the caller runs ``upx -d -f`` on the result::

    python3 upx_recover.py <lib-dir> <out-dir>
    for f in <out-dir>/*.cut; do upx -d -f -o "$f.unp" "$f"; done

It does not decrypt, patch or re-sign anything; it reads already-extracted
``.so`` files and writes truncated copies next to them.
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

# Last "UPX!" occurrence -> the trailer block ends 0x28 bytes after the 4-byte
# magic prefix.  Calibrated against stock UPX (``upx --version`` 5.2.0, self-ID
# string "UPX 6.02 Copyright 1996-2025").
TRAILER_SPAN = 0x28

EXPECTED = {
    "libndktest.so": (0x702C, 95_624, "87d74f001ebf87de2ec8655c63b8d048ec4ecec468f56b3201a6a16bfa80e96f"),
    "libencrypt.so": (0x2834, 77_352, "4142dc6342b2f09543766c54660899c4bae313d7f3be57b404b7228b865db3e4"),
    "libShanYCore.so": (0x202C, 75_456, "88f2ac1cbda9fcc4826ac812558ce47e6f3a196c9a54315324b287f217efc1b1"),
    "libhtsfx.so": (0x10D01, 127_520, "16fb2b4b71c3d7ad20a7f57f3e22946e711cefac8cf78d26101c5f031b0d2515"),
}


def trailer_end(data: bytes) -> int:
    """Return the offset one past the last UPX trailer block."""
    idx = data.rfind(b"UPX!")
    if idx < 0:
        raise ValueError("no UPX! magic")
    return idx - 4 + TRAILER_SPAN


def describe(data: bytes) -> str:
    """Describe the trailer without over-claiming field semantics.

    Only the magic and the raw 0x28-byte block are reported: the exact
    meaning of the tail ``b_info`` fields is what makes ``upx -d`` reject the
    untouched file, so we do not assert a layout here.
    """
    idx = data.rfind(b"UPX!")
    l_info = idx - 4
    block = data[l_info : l_info + TRAILER_SPAN]
    return (
        f"last UPX! at 0x{idx:x}, l_info at 0x{l_info:x}, "
        f"trailer_end=0x{trailer_end(data):x}, file_len=0x{len(data):x}, "
        f"trailer_block={block.hex()}"
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("lib_dir", type=Path, help="directory holding the packed .so files")
    ap.add_argument("out_dir", type=Path, help="directory for truncated copies")
    ap.add_argument("--only", nargs="*", default=None, help="restrict to these basenames")
    args = ap.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)
    wanted = set(args.only) if args.only else set(EXPECTED)
    rc = 0
    for name, (cut, size, sha) in sorted(EXPECTED.items()):
        if name not in wanted:
            continue
        src = args.lib_dir / name
        if not src.is_file():
            print(f"{name}: missing at {src}", file=sys.stderr)
            rc = 1
            continue
        data = src.read_bytes()
        print(f"{name}: {describe(data)}")
        if trailer_end(data) != cut:
            print(f"{name}: trailer end 0x{trailer_end(data):x} != expected 0x{cut:x}", file=sys.stderr)
            rc = 1
        dst = args.out_dir / (name + ".cut")
        dst.write_bytes(data[:cut])
        print(f"{name}: wrote {dst.name} ({cut} B), packed_sha256={hashlib.sha256(data).hexdigest()}")
        print(f"{name}: after `upx -d -f` expect {size} B sha256={sha}")
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
