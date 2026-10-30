#!/usr/bin/env python3
"""Decode a YJB `.r` ZIP member with a supplied position-related keystream.

The public report ships only a 128-byte prefix for smoke tests. A complete
keystream file can be supplied by the analyst; this tool never embeds sample
data or full keystream material.
"""

from __future__ import annotations

import argparse
import hashlib
import zipfile
from pathlib import Path


PREFIX = bytes.fromhex(
    "b309cd5de71c0659d177a802214cc79f43753653df51a1ae9c150cf43d24"
    "a12e0ea293c5235fb1b360d2740e9c004c687783ebc34b1223001143294a"
    "5187808c1a0615461d76632c1d0176fb872bb8c9296c755935da6cff880d"
    "2b5cb056c57985b4e0526ae6b617db3871197acdffa879a7a9d88dc70f36"
    "b08ae93a64fce9f9"
)


def decode(data: bytes, keystream: bytes, partial: bool) -> bytes:
    if len(keystream) < len(data) and not partial:
        raise ValueError(
            f"keystream is shorter than member ({len(keystream)} < {len(data)}); "
            "pass --partial to decode only the known prefix"
        )
    output = bytearray(data)
    width = min(len(data), len(keystream))
    for index in range(width):
        output[index] ^= keystream[index]
    return bytes(output)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="yjb.zip or portal_yjb.zip")
    parser.add_argument("member", help="ZIP member name, e.g. yjb/.../x.js.r")
    parser.add_argument("output", type=Path)
    parser.add_argument("--keystream", type=Path)
    parser.add_argument("--partial", action="store_true")
    args = parser.parse_args()

    keystream = (
        args.keystream.read_bytes()
        if args.keystream
        else PREFIX
    )
    with zipfile.ZipFile(args.archive) as archive:
        payload = archive.read(args.member)
    decoded = decode(payload, keystream, args.partial)
    args.output.write_bytes(decoded)
    print(
        f"member={args.member} ciphertext={len(payload)} keystream={len(keystream)} "
        f"decoded={len(decoded)} sha256={hashlib.sha256(decoded).hexdigest()}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
