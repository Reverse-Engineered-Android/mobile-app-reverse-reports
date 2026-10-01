#!/usr/bin/env python3
"""Census the flattened-control-flow (FLA) dispatchers in an objdump listing.

Pinduoduo native libraries contain two visually similar jump-table idioms:

* **ordinary switch / jump table** -- `ldr xT, [xP, wI, sxtw #3]` followed by
  `br xT`, where `wI` comes straight from the function's own argument or from
  a bounds-clamped value;
* **FLA dispatcher** -- the same load and `br`, but the index is
  `state XOR key` where `state` is a *stack slot* that the dispatcher itself
  writes back before every jump, and `key` is a `csel`-selected constant.

Only the second is obfuscation.  This tool separates the two so the report can
state a per-library FLA count that is not inflated by ordinary switch tables.

A tail is classified FLA when, within the instructions preceding the load:

  1. `ldr wIdx, [sp, #N]` defines the index register (the stack-resident state),
  2. an `eor wIdx, wS, wK` feeds that register, and
  3. a `str wS', [sp, #N]` with the same slot appears between the load and the
     `br` (the state write-back).

Usage:
  fla_census.py <objdump -d output> [--list]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

INSN_RE = re.compile(r"^\s*([0-9a-f]+):\t(?:[0-9a-f ]{8,}\t)?(.*)$")
BR_WINDOW = 0x40      # bytes between the table load and its `br`
BACK_WINDOW = 24      # instructions to look back for state load / eor


def parse(path: Path) -> dict[int, str]:
    insns: dict[int, str] = {}
    for line in path.read_text(errors="replace").splitlines():
        m = INSN_RE.match(line)
        if not m:
            continue
        addr = int(m.group(1), 16)
        text = m.group(2).split("//")[0].split("<")[0].strip()
        if text:
            insns[addr] = re.sub(r"\s+", " ", text)
    return insns


def census(insns: dict[int, str]) -> tuple[list[dict], int]:
    addrs = sorted(insns)
    pos = {a: i for i, a in enumerate(addrs)}
    fla: list[dict] = []
    plain = 0

    for a in addrs:
        text = insns[a]
        if not (text.startswith("ldr x") and "sxtw #3" in text):
            continue
        dest = text.split(",")[0].split()[1]
        i = pos[a]

        br = None
        for j in range(i + 1, min(i + 20, len(addrs))):
            if addrs[j] > a + BR_WINDOW:
                break
            if insns[addrs[j]] == "br %s" % dest:
                br = addrs[j]
                break
        if br is None:
            continue

        try:
            idx_reg = text.split("[")[1].split(",")[1].strip()
        except IndexError:
            continue

        state_slot = None
        for j in range(max(0, i - BACK_WINDOW), i):
            m = re.match(r"^ldr (w\d+), \[sp, #(-?\d+)\]$", insns[addrs[j]])
            if m and m.group(1) == idx_reg:
                state_slot = int(m.group(2))

        xor_fed = any(
            (m := re.match(r"^eor (w\d+), (w\d+), (w\d+)$", insns[addrs[j]]))
            and m.group(1) == idx_reg
            for j in range(max(0, i - BACK_WINDOW), i)
        )

        writeback = False
        if state_slot is not None:
            pat = "[sp, #%d]" % state_slot
            writeback = any(
                insns[addrs[j]].startswith("str w") and pat in insns[addrs[j]]
                for j in range(i + 1, min(i + 20, len(addrs)))
                if addrs[j] <= br
            )

        if state_slot is not None and xor_fed and writeback:
            fla.append({"br": br, "load": a, "state_slot": state_slot})
        else:
            plain += 1

    return fla, plain


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("objdump")
    ap.add_argument("--list", action="store_true", help="list every FLA core")
    args = ap.parse_args()

    insns = parse(Path(args.objdump))
    fla, plain = census(insns)
    print("# parsed %d instructions" % len(insns))
    print("# FLA dispatchers: %d" % len(fla))
    print("# ordinary switch / jump tables: %d" % plain)
    if args.list:
        print()
        print("| br site | table load | state slot |")
        print("| --- | --- | ---: |")
        for f in fla:
            print("| `%#x` | `%#x` | `sp+%#x` |" % (f["br"], f["load"], f["state_slot"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
