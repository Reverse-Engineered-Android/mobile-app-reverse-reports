#!/usr/bin/env python3
"""Audit the DroidGuard native rotating-keystream buffer transform family.

The VM never leaves a fetched buffer in clear text. After the range decoder
(`0x46cb0` / per-variant equivalents) materialises a buffer, an inlined loop
re-encodes it byte by byte with an 8-byte keystream that rotates one byte per
iteration:

    loop:
        ldrb  wD, [xR]
        subs  wN, wN, #1
        <op>  wD, wD, wK      ; add | sub | eor
        ror   xK, xK, #8
        strb  wD, [xR], #1
        b.ne  loop

`wK` is the low word of a 64-bit key that is either built from an immediate
chain in the same basic block or derived at runtime from a VM register.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


LINE_RE = re.compile(r"^\s*([0-9a-f]+):\s+(.*)$")
ARITH_RE = re.compile(r"^(add|sub|eor)\s+(w\d+),\s+(w\d+),\s+(w\d+)$")
ROR_RE = re.compile(r"^ror\s+(x\d+),\s+\1,\s+#(\d+)$")
STRB_RE = re.compile(r"^strb\s+(w\d+),\s+\[(x\d+)\],\s+#1$")
LDRB_RE = re.compile(r"^ldrb\s+(w\d+),\s+\[(x\d+)\]$")
SUBS_RE = re.compile(r"^subs\s+(w\d+),\s+\1,\s+#0x([0-9a-f]+)$")
BRANCH_RE = re.compile(r"^b\.[a-z]+\s+([0-9a-f]+)")
MOV_IMM_RE = re.compile(r"^mov\s+(x\d+),\s+#0x([0-9a-f]+)")
MOVK_RE = re.compile(r"^movk\s+(x\d+),\s+#0x([0-9a-f]+),\s+lsl\s+#(\d+)")
MOV_REG_RE = re.compile(r"^mov\s+(x\d+),\s+(x\d+)$")
LDR_RE = re.compile(r"^ldr\s+(x\d+),\s+\[(x\d+),\s+#(\d+)\]$")


def parse_disassembly(path: Path) -> list[tuple[int, str]]:
    items: list[tuple[int, str]] = []
    for line in path.read_text(errors="replace").splitlines():
        match = LINE_RE.match(line)
        if match:
            items.append((int(match.group(1), 16), match.group(2).strip()))
    return items


def resolve_key(items: list[tuple[int, str]], index: int, key_register: str) -> dict[str, Any]:
    """Resolve the 64-bit key feeding wK from the preceding instructions."""
    registers: dict[str, Any] = {}
    for position in range(max(0, index - 48), index):
        text = items[position][1]
        match = MOV_IMM_RE.match(text)
        if match:
            registers[match.group(1)] = int(match.group(2), 16)
            continue
        match = MOVK_RE.match(text)
        if match:
            register, immediate, shift = match.group(1), int(match.group(2), 16), int(match.group(3))
            mask = 0xFFFF << shift
            previous = registers.get(register)
            previous = previous if isinstance(previous, int) else 0
            registers[register] = (previous & ~mask) | ((immediate & 0xFFFF) << shift)
            continue
        match = MOV_REG_RE.match(text)
        if match:
            registers[match.group(1)] = ("register", match.group(2))
            continue
        match = LDR_RE.match(text)
        if match:
            registers[match.group(1)] = ("load", f"[{match.group(2)}+{match.group(3)}]")
    name = "x" + key_register[1:]
    value = registers.get(name)
    if isinstance(value, int):
        return {"origin": "immediate", "value": value, "value_hex": f"0x{value:016x}"}
    if isinstance(value, tuple):
        kind, detail = value
        return {"origin": "runtime", "source": f"{kind}:{detail}"}
    return {"origin": "runtime", "source": "unresolved"}


def keystream_bytes(value: int, width: int = 8) -> list[int]:
    return [(value >> (8 * i)) & 0xFF for i in range(width)]


def audit(path: Path, variant: str) -> dict[str, Any]:
    items = parse_disassembly(path)
    index_of = {address: position for position, (address, _) in enumerate(items)}
    sites: list[dict[str, Any]] = []
    for position, (address, text) in enumerate(items):
        match = ARITH_RE.match(text)
        if not match or position + 4 >= len(items):
            continue
        destination, key_register = match.group(2), match.group(4)
        ror_match = ROR_RE.match(items[position + 1][1])
        strb_match = STRB_RE.match(items[position + 2][1])
        branch_match = BRANCH_RE.match(items[position + 3][1])
        if not (ror_match and strb_match and branch_match):
            continue
        rotated = ror_match.group(1)
        if strb_match.group(1) != destination:
            continue
        # The rotated register is the keystream register; it is one of the
        # arithmetic sources. `sub wD, wK, wD` keeps the key in the first
        # source, `sub wD, wD, wK` / `add wD, wD, wK` / `eor wD, wD, wK` in
        # the second.
        operands = {match.group(2), match.group(3), match.group(4)}
        rotated_operand = next((item for item in operands if "x" + item[1:] == rotated), None)
        if rotated_operand is None or rotated_operand == destination:
            continue
        key_register = rotated_operand
        rotation = int(ror_match.group(2))
        loop_back = int(branch_match.group(1), 16)
        loop_position = index_of.get(loop_back)
        if loop_position is None or loop_position > position:
            continue
        window = [items[position - offset][1] for offset in range(1, 5)]
        ldrb_match = next((LDRB_RE.match(item) for item in window if LDRB_RE.match(item)), None)
        subs_match = next((SUBS_RE.match(item) for item in window if SUBS_RE.match(item)), None)
        key = resolve_key(items, position, key_register)
        operation = match.group(1)
        destination_is_second_source = match.group(3) == destination
        if operation == "eor":
            transform = "stored = decoded XOR keystream_byte"
            inverse = "decoded = stored XOR keystream_byte"
        elif operation == "add":
            transform = "stored = (decoded + keystream_byte) mod 256"
            inverse = "decoded = (stored - keystream_byte) mod 256"
        elif destination_is_second_source:
            transform = "stored = (decoded - keystream_byte) mod 256"
            inverse = "decoded = (stored + keystream_byte) mod 256"
        else:
            transform = "stored = (keystream_byte - decoded) mod 256"
            inverse = "decoded = (keystream_byte - stored) mod 256"
        entry: dict[str, Any] = {
            "site": f"0x{address:x}",
            "operation": operation,
            "destination_register": destination,
            "key_register": key_register,
            "rotation_bits": rotation,
            "loop_back": f"0x{loop_back:x}",
            "encode": transform,
            "decode": inverse,
            "load_instruction": ldrb_match.group(0) if ldrb_match else None,
            "counter_instruction": subs_match.group(0) if subs_match else None,
            "key": key,
        }
        if key.get("origin") == "immediate":
            entry["keystream_bytes"] = keystream_bytes(key["value"])
        sites.append(entry)
    return {
        "variant": variant,
        "binary_disassembly": path.name,
        "site_count": len(sites),
        "sites": sites,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--variant",
        action="append",
        default=[],
        metavar="NAME=DISASM",
        help="variant label and the disassembly to audit (repeat per variant)",
    )
    parser.add_argument("--root", type=Path, help="analysis root holding evidence/native")
    parser.add_argument("--json-out", type=Path, required=True)
    args = parser.parse_args()
    targets: list[tuple[str, Path]] = []
    for item in args.variant:
        name, _, path = item.partition("=")
        if not name or not path:
            parser.error(f"--variant expects NAME=DISASM, got {item!r}")
        targets.append((name, Path(path)))
    if not targets:
        root = (args.root or Path(__file__).resolve().parents[1]).resolve()
        targets = [("A", root / "evidence/native/base.disasm")]
        for name, filename in (
            ("B", "libd5CC5EA440436.disasm"),
            ("C", "libd7383C18A1562.disasm"),
            ("D", "libd5AF72C7F377B.disasm"),
        ):
            targets.append((name, root / "evidence/native/variants" / filename))
    executed = {
        "A": {
            "0x223e8": {
                "program": "0b527259423eb1507aed800f2b8cf2a0f41a851788f63077357d4811657cdda0.b",
                "flow": "fast",
                "iterations": 17222,
                "destination_buffer": "0x69f000",
                "fetched_buffer_sha256": "1e22bbf8c2cdad5f9a0c190108b878e01bb67a98f267cda4bfa4e221316bf625",
                "stored_buffer_sha256": "f541b3226e27367ac1d0777f88fabdf1b90832ea038345e21b396a04c43919ed",
                "trace": "evidence/native/trace_vm_semantics_0b527259_fast.json",
                "verification": (
                    "stored[i] == (fetched[i] + keystream[i % 8]) mod 256 for all 17222 bytes; "
                    "the inverse recovers the range-decoder output byte for byte"
                ),
            }
        }
    }
    artifact = {
        "schema": "droidguard-buffer-keystream-audit-v1",
        "executed_sites": executed,
        "description": (
            "Static inventory of the inlined rotating-keystream buffer transform "
            "(add/sub/eor with an 8-byte key rotated one byte per iteration) in the "
            "four DroidGuard native variants."
        ),
        "variants": [audit(path, name) for name, path in targets],
    }
    args.json_out.write_text(json.dumps(artifact, indent=2, ensure_ascii=False) + "\n")
    total = sum(item["site_count"] for item in artifact["variants"])
    print(json.dumps({"total_sites": total, "per_variant": {i["variant"]: i["site_count"] for i in artifact["variants"]}}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
