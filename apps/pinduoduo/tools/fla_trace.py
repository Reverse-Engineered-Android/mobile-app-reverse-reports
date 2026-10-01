#!/usr/bin/env python3
"""Resolve a flattened-control-flow (FLA) dispatcher back into a normal CFG.

Some Pinduoduo native libraries compile functions into a state machine.  A
state word lives on the stack; each dispatch step does

    key    = csel(C_else, C_taken, <guard on an argument>)
    index  = state XOR key
    target = <base> + table[index]        table[i] = <block> - <base>
    state  = state XOR key2               key2 = csel(D_else, D_taken, <guard>)
    br target

Every successor is therefore a compile-time constant, and the guard tells you
which argument value takes which edge.  The obfuscation is cosmetic: the real
control-flow graph is statically recoverable.

The tool parses ``objdump -d`` output and abstractly interprets the dispatcher
with *set-valued* registers, because ``csel`` introduces two possible values
that are resolved again later.  Where a register holds two possibilities the
step gets two successors, which is exactly the branch the original code had.

Recognised patterns:

  adrp/add/sub/mov/movz/movk   constant and address arithmetic
  sub Rd, Ra, Rb               table entry = <block> - <base>
  stp/str/ldr  [sp, #N]        the stack-resident state word and the table
  ldr xT, [xP, wIdx, sxtw #3]  table lookup
  cmp + csel                   guard and the two keys
  br                           end of a dispatch step

Usage:
  fla_trace.py <objdump-output> --start 0xbbfb0 [--end 0xbc2b8] [--table-sp 0x10]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

MASK = 0xFFFFFFFFFFFFFFFF
INSN_RE = re.compile(r"^\s*([0-9a-f]+):\t(?:[0-9a-f ]{8,}\t)?(.*)$")
IMM_RE = re.compile(r"#(-?0x[0-9a-f]+|-?\d+)")
MAX_VALUES = 4


def parse_objdump(path: Path) -> dict[int, str]:
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


def imm(operand: str) -> int | None:
    m = IMM_RE.search(operand)
    if not m:
        return None
    token = m.group(1)
    return int(token, 16) if token.lstrip("-").startswith("0x") else int(token)


def reg_of(operand: str) -> str:
    return operand.strip().split(",")[0].strip()


class State:
    """Set-valued register file plus stack slots, pages and code bases."""

    def __init__(self) -> None:
        self.regs: dict[str, set[int]] = {}
        self.pages: dict[str, int] = {}
        self.stack: dict[int, set[int]] = {}
        self.stack_ptr: dict[str, int] = {}
        self.bases: dict[str, set[int]] = {}
        self.code: set[str] = set()   # registers holding a code address

    def clear_volatile(self) -> None:
        """Drop block-local (join-derived) registers, keep block-independent ones.

        The FLA body lays every block out linearly behind one prologue, so the
        constants computed there (table pointer, per-block bases) stay valid.
        Only registers that received two possible values from a ``csel`` are
        block-local: the real state lives on the stack and is reloaded.
        """
        for name in [n for n, v in self.regs.items() if len(v) > 1]:
            self.regs.pop(name, None)
            self.bases.pop(name, None)
        for name in [n for n, v in self.bases.items() if len(v) > 1]:
            self.bases.pop(name, None)

    def get(self, name: str) -> set[int]:
        if name in self.regs:
            return self.regs[name]
        if name in self.pages:
            return {self.pages[name]}
        return set()

    def put(self, name: str, values: set[int]) -> None:
        if not values:
            self.regs.pop(name, None)
            return
        if len(values) > MAX_VALUES:
            values = set(sorted(values)[:MAX_VALUES])
        self.regs[name] = values


def run(insns: dict[int, str], start: int, end: int, table_sp: int) -> list[dict]:
    st = State()
    steps: list[dict] = []
    cur: dict | None = None
    guard = ""
    pc = start

    def new_step() -> dict:
        return {"pc": pc, "guard": guard, "csels": [], "indices": set()}

    while pc in insns and pc < end:
        text = insns[pc]
        op, _, rest = text.partition(" ")
        ops = [o.strip() for o in rest.split(",")] if rest else []

        if op == "adrp":
            m = re.search(r",\s*([0-9a-f]+)\s*$", rest)
            if m:
                rd = reg_of(rest)
                st.pages[rd] = int(m.group(1), 16)
                st.code.add(rd)

        elif op in ("add", "sub") and len(ops) == 3:
            rd, ra, rb = ops
            value = imm(rb)
            if value is not None:
                if ra == "sp":
                    st.stack_ptr[rd] = value
                else:
                    base = st.get(ra)
                    if base:
                        st.put(rd, {(v + value) & MASK if op == "add"
                                    else (v - value) & MASK for v in base})
                        if ra in st.code:
                            st.code.add(rd)
            else:
                a, b = st.get(ra), st.get(rb)
                if a and b:
                    if op == "add" and ra in st.code:
                        # `add Rd, Rcode, Rtable` -> Rd = block address
                        st.bases[rd] = set(a)
                    st.put(rd, {(x + y) & MASK if op == "add" else (x - y) & MASK
                                for x in a for y in b})

        elif op in ("mov", "movz") and len(ops) == 2:
            value = imm(ops[1])
            if value is not None:
                st.put(ops[0], {value})

        elif op == "movk" and len(ops) >= 2:
            value = imm(ops[1])
            m = re.search(r"lsl #(\d+)", rest)
            shift = int(m.group(1)) if m else 0
            if value is not None:
                prior = st.get(ops[0]) or {0}
                st.put(ops[0], {(v & ~(0xFFFF << shift) & MASK) | ((value & 0xFFFF) << shift)
                                for v in prior})

        elif op == "cmp":
            guard = rest
            if cur is not None:
                cur["guard"] = rest

        elif op == "csel" and len(ops) == 4:
            if cur is None:
                cur = new_step()
            a, b = st.get(ops[1]), st.get(ops[2])
            entry = {"cond": ops[3],
                     "fallthrough": sorted(a), "taken": sorted(b)}
            cur["csels"].append(entry)
            # join: the destination now holds either value
            st.put(ops[0], a | b)
            cur.setdefault("joins", []).append({"dst": ops[0],
                                                "else": sorted(a), "taken": sorted(b)})

        elif op == "eor" and len(ops) == 3:
            a, b = st.get(ops[1]), st.get(ops[2])
            if a and b:
                st.put(ops[0], {x ^ y for x in a for y in b})

        elif op == "ldr":
            m = re.match(r"^(\w+),\s*\[sp,\s*#(-?\d+)\]$", rest)
            if m:
                slot = int(m.group(2))
                if slot in st.stack:
                    st.put(m.group(1), set(st.stack[slot]))
            elif "sxtw" in rest:
                m = re.search(r"\[(\w+),\s*(\w+),\s*sxtw", rest)
                if cur is None:
                    cur = new_step()
                if m:
                    base_off = st.stack_ptr.get(m.group(1), table_sp)
                    for idx in sorted(st.get(m.group(2))):
                        cur["indices"].add(idx)
                        if idx in st.stack:
                            cur.setdefault("entries", set()).add(
                                (base_off, next(iter(st.stack[idx]))))
                    cur.setdefault("entry_idx", set()).add(idx)

        elif op in ("stp", "str"):
            # stp Rt, Rt2, [sp, #N]  -> two consecutive 8-byte slots
            m = re.match(r"^(\w+),\s*(\w+),\s*\[sp,\s*#(-?\d+)\]$", rest)
            if m:
                off = int(m.group(3))
                st.stack[off] = set(st.get(m.group(1)))
                st.stack[off + 8] = set(st.get(m.group(2)))
            else:
                m = re.match(r"^(\w+),\s*\[sp,\s*#(-?\d+)\]$", rest)
                if m:
                    st.stack[int(m.group(2))] = set(st.get(m.group(1)))
                else:
                    # `stp Rt, Rt2, [sp, #N]!` and `str Rt, [xP, #N]` forms
                    m = re.match(r"^(\w+),\s*\[(\w+),\s*#(-?\d+)\]!?$", rest)
                    if m and m.group(2) in st.stack_ptr:
                        st.stack[st.stack_ptr[m.group(2)] + int(m.group(3))] = set(st.get(m.group(1)))

        elif op == "br":
            if cur is not None:
                br = reg_of(rest)
                bases = st.bases.get(br, set())
                targets: set[int] = set()
                for base in bases:
                    for _off, entry in cur.get("entries", set()):
                        targets.add((base + entry) & MASK)
                cur["br_reg"] = br
                cur["targets"] = sorted(targets)
                # Table entries are keyed by *element* index: element i lives
                # at byte offset table_sp + 8*i.
                cur["table"] = {i: st.stack[table_sp + 8 * i]
                                for i in range(64)
                                if table_sp + 8 * i in st.stack}
                cur["base_set"] = sorted(bases)
                steps.append(cur)
                cur = None
            st.clear_volatile()

        pc += 4

    if cur is not None:
        steps.append(cur)
    return steps


COND_EVAL = {
    "lt": lambda a, b: a < b, "le": lambda a, b: a <= b,
    "gt": lambda a, b: a > b, "ge": lambda a, b: a >= b,
    "eq": lambda a, b: a == b, "ne": lambda a, b: a != b,
    "hi": lambda a, b: a > b, "ls": lambda a, b: a <= b,
    "hs": lambda a, b: a >= b, "cc": lambda a, b: a < b,
    "cs": lambda a, b: a >= b,
}


def eval_guard(guard: str, cond: str, w0: int) -> bool | None:
    """Return whether `cond` holds for the guard `cmp w0, #imm`."""
    m = re.match(r"^w(\d+),\s*#(-?0x[0-9a-f]+|-?\d+)$", guard)
    if not m or m.group(1) != "0":
        return None
    value = int(m.group(2), 16) if m.group(2).lstrip("-").startswith("0x") else int(m.group(2))
    fn = COND_EVAL.get(cond)
    return fn(w0, value) if fn else None


def simulate(steps: list[dict], w0: int, state: int) -> list[dict]:
    """Follow the state machine for one selector value.

    The dispatch steps are shared by all selectors, so the successor of a block
    is not identified by address: the state word is.  At each iteration we look
    for the step whose table actually contains ``state XOR key`` for this
    selector's guard outcome -- that step is the one the block branched to.
    """
    trail: list[dict] = []
    seen: set[tuple[int, int]] = set()
    for _ in range(64):
        if (0, state) in seen:
            break
        seen.add((0, state))
        found = None
        for step in steps:
            csels = step.get("csels") or []
            if not csels:
                continue
            idx_key = csels[0]
            guard_val = eval_guard(step.get("guard", ""), idx_key["cond"], w0)
            if guard_val is None:
                continue
            key = idx_key["taken"] if guard_val else idx_key["fallthrough"]
            table = step.get("table") or {}
            bases = step.get("base_set") or []
            if not key or not table or not bases:
                continue
            index = state ^ key[0]
            if index in table:
                found = (step, guard_val, index, table[index], bases[0])
                break
        if found is None:
            break
        step, guard_val, index, entry, base = found
        target = (base + next(iter(entry))) & MASK
        nxt = state
        csels = step["csels"]
        if len(csels) > 1:
            st_key = csels[1]
            skey = st_key["taken"] if guard_val else st_key["fallthrough"]
            if skey:
                nxt = state ^ skey[0]
        trail.append({"pc": step["pc"], "guard": step.get("guard"),
                      "cond": csels[0]["cond"], "guard_holds": guard_val,
                      "index": index, "base": base, "target": target,
                      "state": state, "next_state": nxt})
        state = nxt
    return trail


def fmt_values(values: list[int]) -> str:
    return " / ".join(f"`{v:#010x}`" for v in values) if values else "-"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("objdump")
    ap.add_argument("--start", required=True)
    ap.add_argument("--end", default=None)
    ap.add_argument("--table-sp", default="0x10")
    ap.add_argument("--simulate", default=None,
                    help="comma-separated selector values for w0, e.g. 1,2,3,4")
    ap.add_argument("--initial-state", default=None,
                    help="initial FLA state word (hex); inferred when omitted")
    args = ap.parse_args()

    insns = parse_objdump(Path(args.objdump))
    start = int(args.start, 16)
    end = int(args.end, 16) if args.end else start + 0x2000
    print(f"# parsed {len(insns)} instructions; dispatcher {start:#x}..{end:#x}")
    steps = run(insns, start, end, int(args.table_sp, 16))
    if not steps:
        print("no dispatch steps found (wrong --start, or no FLA here)", file=sys.stderr)
        return 1
    print(f"# resolved {len(steps)} dispatch steps\n")
    print("| step | guard | cond | index key (else / taken) | state key (else / taken) | indices | target(s) |")
    print("| ---: | --- | --- | --- | --- | --- | --- |")
    for i, s in enumerate(steps, 1):
        csels = s.get("csels") or []
        idx_key = csels[0] if csels else None
        st_key = csels[1] if len(csels) > 1 else None
        idxs = ",".join(str(v) for v in sorted(s.get("indices") or [])) or "-"
        tgts = ", ".join(f"`{t:#x}`" for t in (s.get("targets") or [])) or "-"
        print(f"| {i} | `{s.get('guard') or '-'}` | {idx_key['cond'] if idx_key else '-'} | "
              f"{fmt_values(idx_key['fallthrough']) if idx_key else '-'} / "
              f"{fmt_values(idx_key['taken']) if idx_key else '-'} | "
              f"{fmt_values(st_key['fallthrough']) if st_key else '-'} / "
              f"{fmt_values(st_key['taken']) if st_key else '-'} | {idxs} | {tgts} |")
    if args.simulate:
        print("\n## selector simulation\n")
        print("| w0 | steps | visited blocks |")
        print("| ---: | ---: | --- |")
        for sel in (int(x, 16) if x.strip().lower().startswith("0x") else int(x)
                    for x in args.simulate.split(",")):
            if args.initial_state:
                state = int(args.initial_state, 16)
            else:
                # state = index XOR key, solved from the first step whose table
                # actually contains a candidate index.
                state = None
                for step in steps:
                    csels = step.get("csels") or []
                    table = step.get("table") or {}
                    if not csels or not table:
                        continue
                    cands = sorted(set(table) & set(step.get("indices") or []))
                    if not cands:
                        continue
                    state = cands[0] ^ (csels[0]["fallthrough"] or [0])[0]
                    break
                if state is None:
                    print("  (could not infer initial state; pass --initial-state)")
                    continue
            trail = simulate(steps, sel, state)
            blocks = " -> ".join(f"`{t['target']:#x}`" for t in trail) or "-"
            print(f"| {sel} | {len(trail)} | {blocks} |")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
