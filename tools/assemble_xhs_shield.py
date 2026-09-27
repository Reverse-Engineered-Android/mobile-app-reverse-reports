#!/usr/bin/env python3
"""Replay the verified outer XHS shield structure with supplied inputs."""

from __future__ import annotations

import argparse
import base64
import os
import struct
import sys


def rc4(key: bytes, data: bytes) -> bytes:
    if not key:
        raise ValueError("RC4 key must not be empty")
    state = list(range(256))
    index = 0
    for position in range(256):
        index = (index + state[position] + key[position % len(key)]) % 256
        state[position], state[index] = state[index], state[position]
    left = right = 0
    output = bytearray()
    for value in data:
        left = (left + 1) % 256
        right = (right + state[left]) % 256
        state[left], state[right] = state[right], state[left]
        output.append(value ^ state[(state[left] + state[right]) % 256])
    return bytes(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-id", type=int, required=True)
    parser.add_argument("--build", required=True)
    parser.add_argument("--device-id", required=True)
    parser.add_argument("--token-type", type=int, required=True)
    parser.add_argument("--digest16", default="00" * 16)
    parser.add_argument("--rc4-key-env", default="XHS_RC4_KEY")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    raw_key = os.environ.get(args.rc4_key_env)
    if not raw_key:
        print(f"missing environment variable: {args.rc4_key_env}", file=sys.stderr)
        return 2
    try:
        digest = bytes.fromhex(args.digest16)
    except ValueError:
        print("digest16 must be hexadecimal", file=sys.stderr)
        return 2
    if len(digest) != 16:
        print("digest16 must decode to 16 bytes", file=sys.stderr)
        return 2
    build = args.build.encode("ascii")
    device_id = args.device_id.encode("utf-8")
    payload = b"".join(
        (
            struct.pack(">IIII", 1, args.app_id, 1, len(build)),
            struct.pack(">II", len(device_id), 16),
            build,
            device_id,
            digest,
        )
    )
    encrypted = rc4(raw_key.encode("utf-8"), payload)
    header = struct.pack(">IIII", 4 | (args.token_type << 16), 1, len(payload), len(payload))
    print("XY" + base64.b64encode(header + encrypted).decode("ascii"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
