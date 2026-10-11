#!/usr/bin/env python3
"""Emulate every inlined DroidGuard native string-decoder loop.

DroidGuard's fixed native code stores short strings byte by byte and decodes
them in place with a per-site keystream.  The loop is spelled several ways, so
this tool does not pattern-match one instruction sequence: it locates each
loop, resolves the buffer base *path-aware* through the real control flow,
then emulates the loop body instruction by instruction for every iteration and
reports the exact keystream and plaintext.

Loop shape (displacement spelling):

    mov  xI, xzr                      ; counter (countdown-to-negative form)
    mov  xK, #key / movk xK, #..., lsl #n
    mov  wS, #sub                     ; rotate-amount base
    mov  wC, #carry                   ; carry base
L:  sub  wR, wS, wI                   ; wR = sub - i
    add  wA, wI, #addend
    ror  xR, xK, wR
    tst  wA, #0x40                    ; Z = ((i + addend) & 0x40) == 0
    add  xT, xB, xI                   ; address = base + i
    cinc wC2, wC, eq
    ldrb wB, [xT, #disp]              ; ciphertext byte
    sub  wR, wR, wI
    sub  xI, xI, #1
    add  wR, wR, wC2
    cmn  xI, #N                       ; loop while (i - N) != 0
    sub  wR, wR, #N
    eor  wR, wR, wB
    strb wR, [xT, #disp]
    b.ne L

Resolution model
----------------
* Registers are a 64-bit file with `w`-write zero-extension, so `mov w8, #imm`
  and a later `sub x8, x8, #1` name the same register.
* The loop head is reached by a branch, so the base register is resolved by
  walking the predecessor chain backwards (fall-through plus branch sources,
  skipping the loop's own back-edge) instead of assuming the nearest textual
  write.  This distinguishes e.g. `x8 = sp + 8` on the taken branch from the
  flag-byte value `x8` holds on the fall-through path.
* When the base is a caller-supplied pointer, the call sites are inspected and
  the region is read out of the caller's frame.
* A guard bit identifies a family of loops, not one buffer, so ciphertext
  reuse requires the owning function, flag slot, bit, and exact address set
  all to match.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from bisect import bisect_right
from pathlib import Path
from typing import Any

LINE_RE = re.compile(r"^\s*([0-9a-f]+):\s+(.*)$")
BRANCH_NE_RE = re.compile(r"^b\.ne\s+([0-9a-f]+)")

MOV_IMM_RE = re.compile(r"^mov\s+([wx]\d+), #(0x[0-9a-f]+|\d+)$")
MOVK_RE = re.compile(r"^movk\s+(x\d+), #(0x[0-9a-f]+|\d+), lsl #(\d+)$")
MOV_ZR_RE = re.compile(r"^mov\s+([wx]\d+), ([xw]zr)$")
MOV_REG_RE = re.compile(r"^mov\s+([wx]\d+), ([wx]\d+)$")
MOV_SP_RE = re.compile(r"^mov\s+(x\d+), sp$")
ADD_REG_RE = re.compile(r"^add\s+([wx]\d+), ([wx]\d+), ([wx]\d+)$")
ADD_IMM_RE = re.compile(r"^add\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
ADD_SP_RE = re.compile(r"^add\s+(x\d+), sp, #(0x[0-9a-f]+|\d+)$")
SUB_REG_RE = re.compile(r"^sub\s+([wx]\d+), ([wx]\d+), ([wx]\d+)$")
SUB_IMM_RE = re.compile(r"^sub\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
SUBS_REG_RE = re.compile(r"^subs\s+([wx]\d+), ([wx]\d+), ([wx]\d+)$")
SUBS_IMM_RE = re.compile(r"^subs\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
NEG_RE = re.compile(r"^neg\s+([wx]\d+), ([wx]\d+)$")
MVN_RE = re.compile(r"^mvn\s+([wx]\d+), ([wx]\d+)$")
EOR_REG_RE = re.compile(r"^eor\s+([wx]\d+), ([wx]\d+), ([wx]\d+)$")
EOR_IMM_RE = re.compile(r"^eor\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
AND_IMM_RE = re.compile(r"^and\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
ANDS_IMM_RE = re.compile(r"^ands\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
ORR_IMM_RE = re.compile(r"^orr\s+([wx]\d+), ([wx]\d+), #(0x[0-9a-f]+|\d+)$")
ROR_RE = re.compile(r"^ror\s+(x\d+), (x\d+), (x\d+)$")
TST_IMM_RE = re.compile(r"^tst\s+(w\d+), #(0x[0-9a-f]+|\d+)$")
CINC_RE = re.compile(r"^cinc\s+(w\d+), (w\d+), eq$")
CMN_IMM_RE = re.compile(r"^cmn\s+(x\d+), #(0x[0-9a-f]+|\d+)$")

LDRB_IDX_RE = re.compile(r"^ldrb\s+(w\d+), \[(x\d+), (x\d+)\]$")
LDRB_DISP_RE = re.compile(r"^ldrb\s+(w\d+), \[(x\d+), #(\d+)\]$")
LDRB_PLAIN_RE = re.compile(r"^ldrb\s+(w\d+), \[(x\d+)\]$")
STRB_IDX_RE = re.compile(r"^strb\s+(w\d+), \[(x\d+), (x\d+)\]$")
STRB_DISP_RE = re.compile(r"^strb\s+(w\d+), \[(x\d+), #(\d+)\]$")
STRB_PLAIN_RE = re.compile(r"^strb\s+(w\d+), \[(x\d+)\]$")
STRB_SP_RE = re.compile(r"^strb\s+w(\d+), \[sp, #(\d+)\]$")
STRB_OFF_RE = re.compile(r"^strb\s+w(\d+), \[x0, #(\d+)\]$")
STRB_X0_RE = re.compile(r"^strb\s+w(\d+), \[x0\]$")
STRB_X0_POST_RE = re.compile(r"^strb\s+w(\d+), \[x0\], #(\d+)$")
STRB_OFF_POST_RE = re.compile(r"^strb\s+w(\d+), \[x0, #(\d+)\], #(\d+)$")
STRB_SP_ZR_RE = re.compile(r"^strb\s+wzr, \[sp, #(\d+)\]$")
LDR_SLOT_RE = re.compile(r"^ldr\s+(x\d+), \[sp, #(\d+)\]$")
ADR_RE = re.compile(r"^adr\s+(x\d+), ")
BL_RE = re.compile(r"^bl\s+([0-9a-f]+)")

WRITE_RE = re.compile(
    r"^(?:mov|movk|movz|add|adds|sub|subs|neg|negs|mvn|eor|and|ands|orr|ldr|ldrb|ldrh|"
    r"adr|adrp|ror|mul|sxtw|uxtw|sxtb|sxth|uxtb|uxth|extr|rev|clz|cls|smull|umull|csel|csinc)\s+"
    r"([wx]\d+)[,\s]"
)
TERMINATOR_RE = re.compile(r"^(ret|br|blr|b|b\.\w+|cbz|cbnz|tbz|tbnz)\b")
BRANCH_ANY_RE = re.compile(r"^(?:b|b\.\w+|cbz|cbnz|tbz|tbnz)\s+(?:[xw]\d+, )?(?:#?[\w]+, )?([0-9a-f]+)\b")
# `b.ne` etc. are *conditional*: `^b\b` would wrongly match them and drop the
# fall-through edge, so compare the mnemonic exactly.
UNCOND_MNEMONICS = frozenset({"ret", "br", "b"})


def mnemonic(text: str) -> str:
    return text.split(None, 1)[0] if text else ""


def is_unconditional(text: str) -> bool:
    """True when control cannot fall through to the next instruction."""
    return mnemonic(text) in UNCOND_MNEMONICS

MASK32 = 0xFFFFFFFF
MASK64 = 0xFFFFFFFFFFFFFFFF
SCAN_LIMIT = 8000
UNKNOWN = ("unknown",)


def regnum(name: str) -> int:
    return int(name[1:])


def strip_comment(text: str) -> str:
    return re.sub(r"\s*//.*$", "", text).strip()


def parse(path: Path) -> list[tuple[int, str]]:
    items: list[tuple[int, str]] = []
    for line in path.read_text(errors="replace").splitlines():
        match = LINE_RE.match(line)
        if match:
            items.append((int(match.group(1), 16), strip_comment(match.group(2))))
    return items


def ror64(value: int, amount: int) -> int:
    amount &= 63
    if not amount:
        return value & MASK64
    return ((value >> amount) | (value << (64 - amount))) & MASK64


def read(regs: dict[int, int], name: str) -> int:
    value = regs.get(regnum(name), 0)
    return value & MASK32 if name.startswith("w") else value


def write(regs: dict[int, int], name: str, value: int) -> None:
    regs[regnum(name)] = (value & MASK32) if name.startswith("w") else (value & MASK64)


def writes_to(text: str, name: str) -> bool:
    match = WRITE_RE.match(text)
    if not match:
        return False
    return match.group(1) == name


def resolve_imm(items, at: int, register: str, limit: int = 128):
    """Value of `register` immediately before instruction index `at`."""
    num = regnum(register)
    value: int | None = None
    for position in range(at - 1, max(0, at - limit), -1):
        text = items[position][1]
        match = MOVK_RE.match(text)
        if match and regnum(match.group(1)) == num:
            shift = int(match.group(3))
            mask = 0xFFFF << shift
            base = value if value is not None else 0
            value = (base & ~mask) | ((int(match.group(2), 0) & 0xFFFF) << shift)
            continue
        match = MOV_IMM_RE.match(text)
        if match and regnum(match.group(1)) == num:
            immediate = int(match.group(2), 0)
            if match.group(1).startswith("w"):
                return immediate & MASK32
            if immediate & ~0xFFFF:
                return immediate & MASK64
            base = value if value is not None else 0
            return ((base & ~0xFFFF) | (immediate & 0xFFFF)) & MASK64
        match = MOV_ZR_RE.match(text)
        if match and regnum(match.group(1)) == num:
            return (value or 0) & MASK64
        if writes_to(text, "x%d" % num) or writes_to(text, "w%d" % num):
            return value & MASK64 if value is not None else None
    return value & MASK64 if value is not None else None


def shift_sym(sym, delta: int):
    if sym is None or sym[0] in ("unknown", "imm"):
        return None
    if sym[0] == "frame":
        return ("frame", sym[1] + delta)
    if sym[0] == "slot":
        return ("slot", sym[1] + delta)
    if sym[0] == "arg":
        return ("arg", sym[1], (sym[2] if len(sym) > 2 else 0) + delta)
    return sym


def addr_key(sym, offset: int, function: int | None = None):
    """Address key for `sym + offset`.

    Frame keys carry the owning function so the same stack offset in two
    functions can never be confused: each function has its own `sp`.
    """
    if sym is None:
        return None
    if sym[0] == "frame":
        return ("frame", function, sym[1] + offset)
    if sym[0] == "slot":
        return ("slot", sym[1], offset)
    if sym[0] == "arg":
        return ("arg", sym[1], (sym[2] if len(sym) > 2 else 0) + offset)
    if sym[0] == "text":
        return ("text", sym[1], offset)
    return None


class Unsupported(Exception):
    pass


class Disassembly:
    """One native variant: instructions plus CFG-derived lookup helpers."""

    def __init__(self, path: Path, name: str):
        self.path = path
        self.name = name
        self.items = parse(path)
        self.index_of = {addr: i for i, (addr, _) in enumerate(self.items)}
        self._value_cache: dict[Any, Any] = {}
        self._store_cache: dict[int, dict] = {}
        self.branch_map: dict[int, list[int]] = {}
        entries = {self.items[0][0]}
        for addr, text in self.items:
            match = BRANCH_ANY_RE.match(text)
            if match:
                self.branch_map.setdefault(int(match.group(1), 16), []).append(addr)
            call = BL_RE.match(text)
            if call:
                entries.add(int(call.group(1), 16))
        self.entries = sorted(entries)

    def predecessors(self, addr: int) -> list[int]:
        preds: list[int] = []
        position = self.index_of.get(addr)
        if position is not None and position > 0:
            previous_addr, previous_text = self.items[position - 1]
            if not is_unconditional(previous_text):
                preds.append(previous_addr)
        for source in self.branch_map.get(addr, []):
            if source not in preds:
                preds.append(source)
        return preds

    def value_at(self, addr, register, skip=(), depth=0, seen=None):
        """Symbolic value of `register` on entry to the instruction at `addr`.

        Memoized per (address, register, loop-skip) so a function with many
        decode loops resolves its control flow once.
        """
        if depth > 400:
            return UNKNOWN
        key = (addr, regnum(register), tuple(skip))
        if key in self._value_cache:
            return self._value_cache[key]
        seen = set() if seen is None else seen
        inner = (addr, regnum(register))
        if inner in seen:
            return UNKNOWN
        seen = seen | {inner}
        position = self.index_of.get(addr)
        if position is None:
            self._value_cache[key] = UNKNOWN
            return UNKNOWN
        preds = [p for p in self.predecessors(addr) if not (len(skip) == 2 and skip[0] <= p <= skip[1])]
        if not preds:
            result = ("arg", "x%d" % regnum(register))
            self._value_cache[key] = result
            return result
        values = []
        for pred in preds:
            pred_text = self.items[self.index_of[pred]][1]
            if writes_to(pred_text, "x%d" % regnum(register)) or writes_to(pred_text, "w%d" % regnum(register)):
                values.append(self.def_value(pred, register, pred_text, skip, depth + 1, seen))
            else:
                values.append(self.value_at(pred, register, skip, depth + 1, seen))
        first = values[0]
        for other in values[1:]:
            if other != first:
                self._value_cache[key] = UNKNOWN
                return UNKNOWN
        self._value_cache[key] = first
        return first

    def def_value(self, at, register, text, skip, depth, seen):
        match = ADD_SP_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("frame", int(match.group(2), 0))
        match = MOV_SP_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("frame", 0)
        match = MOV_REG_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return self.value_at(at, match.group(2), skip, depth + 1, seen)
        match = ADD_IMM_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            inner = self.value_at(at, match.group(2), skip, depth + 1, seen)
            if inner[0] in ("unknown", "imm"):
                return UNKNOWN
            return shift_sym(inner, int(match.group(3), 0))
        match = LDR_SLOT_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("slot", int(match.group(2)))
        match = ADR_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("text", at)
        match = MOV_IMM_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("imm", int(match.group(2), 0))
        match = MOV_ZR_RE.match(text)
        if match and regnum(match.group(1)) == regnum(register):
            return ("imm", 0)
        return UNKNOWN

    def helper_writes(self, callee: int, frame_offset: int, function: int, limit: int = 4000):
        """Stores a helper makes through `x0` when the caller passed `sp+off`.

        DroidGuard's string helpers are called as `add x0, sp, #N; bl helper`
        and fill `[x0 + k]`.  Mapping those writes onto the caller's frame is
        what makes the caller's later decode loop readable.
        """
        writes: dict[Any, tuple[int, int]] = {}
        start = self.index_of.get(callee)
        if start is None:
            return writes
        registers: dict[int, int] = {}
        for position in range(start, min(start + limit, len(self.items))):
            text = self.items[position][1]
            if text.startswith("ret"):
                break
            match = MOV_IMM_RE.match(text)
            if match and match.group(1).startswith("w"):
                registers[regnum(match.group(1))] = int(match.group(2), 0) & 0xFF
                continue
            for pattern in (STRB_OFF_RE, STRB_X0_RE, STRB_X0_POST_RE, STRB_OFF_POST_RE):
                match = pattern.match(text)
                if match:
                    offset = int(match.group(2), 0) if match.lastindex and match.lastindex >= 2 else 0
                    writes[("frame", function, frame_offset + offset)] = (
                        position, registers.get(regnum("w" + match.group(1)), 0))
                    break
        return writes

    def stores_reaching(self, head_addr: int, wanted: set, skip=(), limit: int = 20000):
        """Stores that reach `head_addr`, walking the control flow backwards.

        A linear scan picks whichever store is textually nearest, which is wrong
        when two alternative blocks initialize the same stack region on
        different paths.  Walking predecessors records the *dominating* store
        for each address instead, so a loop guarded by flag bit 2 reads the
        bit-2 initialization rather than whichever block happens to precede it.
        """
        found: dict[Any, tuple[int, int]] = {}
        registers: dict[int, int] = {}
        # forward pass to capture `mov wN, #imm` values feeding each store
        pending = [head_addr]
        visited: set[int] = set()
        while pending and len(visited) < limit:
            addr = pending.pop()
            if addr in visited:
                continue
            visited.add(addr)
            position = self.index_of.get(addr)
            if position is None:
                continue
            text = self.items[position][1]
            key = None
            match = STRB_SP_RE.match(text)
            if match:
                key = self.frame_key(position, int(match.group(2)))
            if key is None:
                match = STRB_SP_ZR_RE.match(text)
                if match:
                    key = self.frame_key(position, int(match.group(1)))
            if key is None:
                match = STRB_DISP_RE.match(text)
                if match:
                    key = self.key_for(position, match.group(2), int(match.group(3)))
            if key is None:
                match = STRB_PLAIN_RE.match(text)
                if match:
                    key = self.key_for(position, match.group(2), 0)
            if key is not None and key in wanted and key not in found:
                found[key] = (position, self.store_value(position, text))
                continue
            for pred in self.predecessors(addr):
                if skip and skip[0] <= pred <= skip[1]:
                    continue
                if pred not in visited:
                    pending.append(pred)
        return found

    def guard_bit(self, head_addr: int):
        """The `(register, bit)` a decode loop is gated on.

        A decode loop sits behind `ldrb wN, [sp, #k]; tbz/tbnz wN, #bit, skip`.
        That bit is the loop's "already decoded" marker, so it also identifies
        which initialization block built the buffer: two decoders gated on the
        same bit read the same block, even when a third block with a different
        bit lies between them in the instruction stream.
        """
        position = self.index_of.get(head_addr)
        if position is None:
            return None
        for index in range(position - 1, max(0, position - 40), -1):
            text = self.items[index][1]
            match = re.match(r"^(?:tbz|tbnz)\s+(w\d+), #(\d+),", text)
            if match:
                slot = None
                for back in range(index - 1, max(0, index - 6), -1):
                    load = re.match(rf"^ldrb\s+{match.group(1)}, \[sp, #(\d+)\]$",
                                    self.items[back][1])
                    if not load:
                        load = re.match(rf"^ldrsb\s+{match.group(1)}, \[sp, #(\d+)\]$",
                                        self.items[back][1])
                    if load:
                        slot = int(load.group(1))
                        break
                return (match.group(1), int(match.group(2)), slot)
            if is_unconditional(text) or text.startswith("ret"):
                break
        return None

    def store_value(self, position: int, text: str) -> int:
        """Byte value a `strb` writes, from the nearest preceding `mov wN, #imm`."""
        match = STRB_SP_RE.match(text) or STRB_DISP_RE.match(text) or STRB_PLAIN_RE.match(text)
        if not match:
            return 0
        name = "w" + match.group(1)
        value = resolve_imm(self.items, position, name)
        return value & 0xFF if isinstance(value, int) else 0

    def linear_sym(self, position: int, register: str, depth: int = 0):
        """Symbolic pointer value from the nearest textual definition.

        Used as a fallback when the control-flow walk cannot resolve a register
        that is written once in a shared tail block far from the loop.
        """
        if depth > 32:
            return None
        num = regnum(register)
        for index in range(position - 1, max(0, position - SCAN_LIMIT), -1):
            text = self.items[index][1]
            match = ADD_SP_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("frame", int(match.group(2), 0))
            match = MOV_SP_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("frame", 0)
            match = MOV_REG_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return self.linear_sym(index, match.group(2), depth + 1)
            match = ADD_IMM_RE.match(text)
            if match and regnum(match.group(1)) == num:
                inner = self.linear_sym(index, match.group(2), depth + 1)
                return shift_sym(inner, int(match.group(3), 0))
            match = LDR_SLOT_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("slot", int(match.group(2)))
            match = ADR_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("text", index)
            match = MOV_IMM_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("imm", int(match.group(2), 0))
            match = MOV_ZR_RE.match(text)
            if match and regnum(match.group(1)) == num:
                return ("imm", 0)
            if writes_to(text, "x%d" % num) or writes_to(text, "w%d" % num):
                return None
        return None

    def frame_key(self, position: int, offset: int):
        """Frame address key tagged with the function owning instruction `position`."""
        return ("frame", self.function_start(self.items[position][0]), offset)

    def key_for(self, position: int, register: str, offset: int):
        """Address key for `[register + offset]` at instruction `position`."""
        symbol = self.value_at(self.items[position][0], register)
        if symbol[0] == "frame":
            return self.frame_key(position, symbol[1] + offset)
        return addr_key(symbol, offset)

    def callers_of(self, target: int) -> list[int]:
        return [addr for addr, text in self.items
                if BL_RE.match(text) and int(BL_RE.match(text).group(1), 16) == target]

    def function_start(self, addr: int) -> int:
        """Owning function entry for `addr`, from the call graph.

        The call graph is the only reliable boundary in this image: the code is
        one dense blob of shared tail blocks, so scanning backwards for `ret`
        happily splits a function in the middle of a block.  A function entry is
        any `bl` target, and `addr` belongs to the greatest entry at or below it.
        """
        position = bisect_right(self.entries, addr) - 1
        return self.entries[position] if position >= 0 else self.entries[0]

    def stores(self, upto: int, limit=SCAN_LIMIT):
        """`strb` stores before index `upto`, keyed by symbolic address.

        Frame-relative keys carry the owning function's start address: every
        function has its own `sp`, so `('frame', sp+8)` in one function is a
        different location from `('frame', sp+8)` in the next.
        """
        if upto in self._store_cache:
            return self._store_cache[upto]
        registers: dict[int, int] = {}
        stores: dict[Any, tuple[int, int]] = {}
        for position in range(max(0, upto - limit), upto):
            text = self.items[position][1]
            match = MOV_IMM_RE.match(text)
            if match and match.group(1).startswith("w"):
                registers[regnum(match.group(1))] = int(match.group(2), 0) & 0xFF
                continue
            match = STRB_SP_RE.match(text)
            if match:
                stores[self.frame_key(position, int(match.group(2)))] = (
                    position, registers.get(int(match.group(1)), 0))
                continue
            match = STRB_SP_ZR_RE.match(text)
            if match:
                stores[self.frame_key(position, int(match.group(1)))] = (position, 0)
                continue
            match = STRB_DISP_RE.match(text)
            if match:
                key = self.key_for(position, match.group(2), int(match.group(3)))
                if key is not None:
                    stores[key] = (position, registers.get(regnum(match.group(1)), 0))
                continue
            match = STRB_PLAIN_RE.match(text)
            if match:
                key = self.key_for(position, match.group(2), 0)
                if key is not None:
                    stores[key] = (position, registers.get(regnum(match.group(1)), 0))
        self._store_cache[upto] = stores
        return stores


def signed_counter(regs: dict[int, int], name: str) -> int:
    """Counter value as a signed integer.

    The countdown-to-negative spelling runs the index 0, -1, -2, … so the
    two's-complement register value must be read signed to compute `base + i`.
    """
    value = read(regs, name)
    return value - (1 << 64) if value >= (1 << 63) else value


def execute(text, regs, memory, flags, addr_of, counter):
    match = MOV_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), int(match.group(2), 0))
        return
    match = MOVK_RE.match(text)
    if match:
        name = match.group(1)
        shift = int(match.group(3))
        mask = 0xFFFF << shift
        regs[regnum(name)] = ((read(regs, name) & ~mask) | ((int(match.group(2), 0) & 0xFFFF) << shift)) & MASK64
        return
    match = MOV_ZR_RE.match(text)
    if match:
        write(regs, match.group(1), 0)
        return
    match = MOV_REG_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)))
        return
    match = ADD_REG_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) + read(regs, match.group(3)))
        return
    match = ADD_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) + int(match.group(3), 0))
        return
    match = SUB_REG_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) - read(regs, match.group(3)))
        return
    match = SUBS_REG_RE.match(text)
    if match:
        result = read(regs, match.group(2)) - read(regs, match.group(3))
        write(regs, match.group(1), result)
        flags["z"] = (result & MASK64) == 0
        return
    match = SUB_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) - int(match.group(3), 0))
        return
    match = SUBS_IMM_RE.match(text)
    if match:
        result = read(regs, match.group(2)) - int(match.group(3), 0)
        write(regs, match.group(1), result)
        flags["z"] = (result & MASK64) == 0
        return
    match = NEG_RE.match(text)
    if match:
        write(regs, match.group(1), -read(regs, match.group(2)))
        return
    match = MVN_RE.match(text)
    if match:
        write(regs, match.group(1), ~read(regs, match.group(2)))
        return
    match = EOR_REG_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) ^ read(regs, match.group(3)))
        return
    match = EOR_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) ^ int(match.group(3), 0))
        return
    match = AND_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) & int(match.group(3), 0))
        return
    match = ANDS_IMM_RE.match(text)
    if match:
        result = read(regs, match.group(2)) & int(match.group(3), 0)
        write(regs, match.group(1), result)
        flags["z"] = result == 0
        return
    match = ORR_IMM_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) | int(match.group(3), 0))
        return
    match = ROR_RE.match(text)
    if match:
        write(regs, match.group(1), ror64(read(regs, match.group(2)), read(regs, match.group(3))))
        return
    match = TST_IMM_RE.match(text)
    if match:
        flags["z"] = (read(regs, match.group(1)) & int(match.group(2), 0)) == 0
        return
    match = CINC_RE.match(text)
    if match:
        write(regs, match.group(1), read(regs, match.group(2)) + (1 if flags.get("z") else 0))
        return
    match = CMN_IMM_RE.match(text)
    if match:
        flags["z"] = ((read(regs, match.group(1)) + int(match.group(2), 0)) & MASK64) == 0
        return
    for pattern in (LDRB_IDX_RE, LDRB_DISP_RE, LDRB_PLAIN_RE):
        match = pattern.match(text)
        if match:
            address = addr_of(match, regs, counter)
            if address not in memory:
                raise Unsupported(f"unmodelled load at {address!r}")
            write(regs, match.group(1), memory[address])
            return
    for pattern in (STRB_IDX_RE, STRB_DISP_RE, STRB_PLAIN_RE):
        match = pattern.match(text)
        if match:
            address = addr_of(match, regs, counter)
            memory[address] = read(regs, match.group(1)) & 0xFF
            return
    raise Unsupported(text)


def locate_loop(dis: Disassembly, head_index: int, back_index: int):
    """Static description of the decode loop spanning [head_index, back_index)."""
    body = [text for _, text in dis.items[head_index:back_index]]
    if not 5 <= len(body) <= 30:
        return None
    if sum(1 for text in body if TST_IMM_RE.match(text)) != 1:
        return None
    if sum(1 for text in body if CINC_RE.match(text)) != 1:
        return None
    if sum(1 for text in body if ROR_RE.match(text)) != 1:
        return None
    if not any(text.startswith("ldrb") for text in body):
        return None
    if not any(text.startswith("strb") for text in body):
        return None

    counter = None
    exit_form = None
    exit_extra = None
    for text in body:
        match = SUBS_IMM_RE.match(text)
        if match and int(match.group(3), 0) == 1 and match.group(1) == match.group(2):
            counter, exit_form = match.group(1), "countdown-to-one"
            continue
        match = SUB_IMM_RE.match(text)
        if match and int(match.group(3), 0) == 1 and match.group(1) == match.group(2):
            counter, exit_form = match.group(1), "countdown-to-negative"
            continue
        match = CMN_IMM_RE.match(text)
        if match:
            exit_extra = int(match.group(2), 0)
    if counter is None:
        return None
    if exit_form == "countdown-to-negative" and exit_extra is None:
        return None

    if exit_form == "countdown-to-one":
        counter_init = resolve_imm(dis.items, head_index, counter)
        if not isinstance(counter_init, int) or counter_init <= 0:
            return None
        iterations = list(range(counter_init, 0, -1))
    else:
        iterations = list(range(0, -exit_extra, -1))
    if not iterations:
        return None

    base_register = None
    displacement = 0
    address_form = None
    for text in body:
        match = LDRB_IDX_RE.match(text)
        if match and match.group(3) == counter:
            base_register, address_form = match.group(2), "index"
            continue
        match = LDRB_DISP_RE.match(text)
        if match:
            for other in body:
                add = ADD_REG_RE.match(other)
                if add and add.group(1) == match.group(2) and add.group(3) == counter:
                    base_register, address_form, displacement = add.group(2), "displacement", int(match.group(3))
    if base_register is None:
        return None

    ror_match = ROR_RE.match(next(text for text in body if ROR_RE.match(text)))
    cinc_match = CINC_RE.match(next(text for text in body if CINC_RE.match(text)))
    return {
        "head": dis.items[head_index][0],
        "back": dis.items[back_index][0],
        "head_index": head_index,
        "back_index": back_index,
        "body": body,
        "counter": counter,
        "exit_form": exit_form,
        "exit_extra": exit_extra,
        "iterations": iterations,
        "base_register": base_register,
        "displacement": displacement,
        "address_form": address_form,
        "rotate_key": resolve_imm(dis.items, head_index, ror_match.group(2)),
        "carry_base": resolve_imm(dis.items, head_index, cinc_match.group(2)),
    }


def emulation_env(dis: Disassembly, info):
    """Pre-loop constant environment plus the loop-control constants."""
    env: dict[int, int] = {}
    for name in sorted(set(re.findall(r"\b[wx]\d+\b", " ".join(info["body"])))):
        value = resolve_imm(dis.items, info["head_index"], name)
        if value is None:
            continue
        if name.startswith("x") or regnum(name) not in env:
            env[regnum(name)] = value
    env[regnum(info["counter"])] = info["iterations"][0] & MASK64
    return env


def run_loop(dis: Disassembly, info, base_sym, ciphertext, addresses, function):
    """Emulate the loop body over `ciphertext`; returns (decoded, keystream).

    `addresses` are the function-tagged keys for each iteration, in order.
    """
    iterations = info["iterations"]
    env = emulation_env(dis, info)
    address_register: dict[str, str] = {}
    for text in info["body"]:
        match = ADD_REG_RE.match(text)
        if match and match.group(3) == info["counter"]:
            address_register[match.group(1)] = match.group(2)

    def key_for_offset(offset: int):
        """Address key for `base + offset`, tagged with the owning function."""
        return addr_key(base_sym, offset, function)

    def addr_of(match, regs, index_name):
        """Effective address of a load/store, relative to the loop's base.

        Index form `[xB, xI]`: base + i.
        Displacement form `add xT, xB, xI` + `[xT, #disp]`: base + i + disp.
        """
        groups = match.groups()
        register = groups[1]
        if len(groups) == 3 and groups[2].startswith("x"):
            if groups[2] != index_name:
                return None
            return key_for_offset(signed_counter(regs, index_name))
        if len(groups) == 3:
            displacement = int(groups[2])
            if register in address_register:
                return key_for_offset(addr_of.iteration + displacement)
            return key_for_offset(displacement)
        return key_for_offset(0)

    addr_of.iteration = 0
    memory: dict[Any, int] = dict(zip(addresses, ciphertext))
    keystream_by_address: dict[Any, int] = {}
    for step, i in enumerate(iterations):
        regs = dict(env)
        regs[regnum(info["counter"])] = i & MASK64
        # `add xT, xBase, xI` runs before the counter is decremented, so the
        # temporary holds `base + i` for this iteration.  Track it explicitly:
        # recomputing from the register file would use the already-decremented
        # counter and shift every access by one.
        addr_of.iteration = i
        flags: dict[str, bool] = {}
        for text in info["body"]:
            execute(text, regs, memory, flags, addr_of, info["counter"])
        keystream_by_address[addresses[step]] = memory[addresses[step]] ^ ciphertext[step]
    order = sorted(range(len(addresses)), key=lambda step: addresses[step])
    decoded = bytes(memory[addresses[step]] for step in order)
    keystream = bytes(keystream_by_address[addresses[step]] for step in order)
    ordered = [addresses[step] for step in order]
    return decoded, keystream, ordered


def analyse(dis: Disassembly) -> dict[str, Any]:
    sites: list[dict[str, Any]] = []
    seen: set[int] = set()
    # Guard shape -> initialization blocks already proven for that exact region.
    guard_blocks: dict[Any, dict] = {}

    for position, (addr, text) in enumerate(dis.items):
        match = BRANCH_NE_RE.match(text)
        if not match:
            continue
        target = int(match.group(1), 16)
        if target >= addr or target not in dis.index_of:
            continue
        head_index = dis.index_of[target]
        if head_index in seen:
            continue
        info = locate_loop(dis, head_index, position)
        if info is None:
            continue
        seen.add(head_index)

        function = dis.function_start(info["head"])
        record: dict[str, Any] = {
            "site": f"0x{info['head']:x}",
            "loop_back": f"0x{info['back']:x}",
            "address_form": info["address_form"],
            "base_register": info["base_register"],
            "displacement": info["displacement"],
            "index_register": info["counter"],
            "exit_form": info["exit_form"],
            "exit_extra": info["exit_extra"],
            "iteration_count": len(info["iterations"]),
            "index_start": info["iterations"][0],
            "index_end": info["iterations"][-1],
            "body_length": len(info["body"]),
            "rotate_key": hex(info["rotate_key"]) if isinstance(info["rotate_key"], int) else None,
            "carry_base": info["carry_base"],
        }
        base_sym = dis.value_at(info["head"], info["base_register"], skip=(info["head"], info["back"]))
        base_resolution = "control-flow"
        if base_sym is None or base_sym[0] == "unknown":
            # The CFG walk can give up on a register set far up a shared tail
            # block; fall back to the nearest textual definition.
            fallback = dis.linear_sym(info["head_index"], info["base_register"])
            if fallback is not None and fallback[0] != "unknown":
                base_sym = fallback
                base_resolution = "linear"
        record["base_kind"] = base_sym[0] if base_sym else None
        record["base_resolution"] = base_resolution

        frame_sym = base_sym
        stores_owner = dis
        caller_addr = None
        if base_sym and base_sym[0] == "arg":
            callers = dis.callers_of(function)
            resolved = []
            for caller in callers:
                caller_sym = dis.value_at(caller, base_sym[1], skip=(info["head"], info["back"]))
                if caller_sym[0] in ("unknown", "imm", "arg"):
                    resolved = []
                    break
                resolved.append((caller, caller_sym))
            if resolved:
                first = resolved[0][1]
                if all(sym == first for _, sym in resolved):
                    frame_sym = first
                    caller_addr = resolved[0][0]
                    stores_owner = dis
        owner_function = stores_owner.function_start(
            caller_addr if caller_addr is not None else info["head"])
        guard = dis.guard_bit(info["head"])
        record["guard_bit"] = f"{guard[0]} bit {guard[1]}" if guard else None
        # Key the guard on (function, flag slot, bit): the same flag byte is
        # reloaded into different registers before different decoders.
        guard_key = ((owner_function, guard[2], guard[1]) if guard else None)
        region_shape = (
            (guard_key, info["address_form"], info["displacement"],
             tuple(info["iterations"]))
            if guard_key is not None else None
        )
        candidates = guard_blocks.get(region_shape, [])
        if frame_sym is None or frame_sym[0] in ("unknown", "imm", "slot", "text"):
            offsets = {item[0] for item in candidates}
            if len(offsets) == 1:
                frame_sym = ("frame", offsets.pop())
                record["base_kind"] = "frame"
                record["base_resolution"] = "same-guard-block"
                record["stack_base_offset"] = frame_sym[1]
        if frame_sym is None or frame_sym[0] in ("unknown", "imm", "slot", "text"):
            record["ciphertext_source"] = f"{record['base_kind']}-base"
            sites.append(record)
            continue

        addresses = [addr_key(frame_sym, info["displacement"] + i, owner_function)
                     for i in info["iterations"]]
        canonical = next(
            (item for item in candidates if item[1] == tuple(addresses)), None)
        upto = stores_owner.index_of[caller_addr] if caller_addr is not None else info["head_index"]
        stores = dict(stores_owner.stores(upto))
        if caller_addr is not None:
            # The caller builds the buffer through helpers (`add x0, sp, #N; bl
            # helper`), so pull each helper's `[x0 + k]` writes onto the frame.
            container = stores_owner.index_of[caller_addr]
            start = stores_owner.index_of[stores_owner.function_start(caller_addr)]
            for position in range(start, container + 1):
                text = stores_owner.items[position][1]
                call = BL_RE.match(text)
                if not call:
                    continue
                argument = stores_owner.value_at(stores_owner.items[position][0], "x0")
                if argument[0] != "frame" or argument[1] != frame_sym[1]:
                    continue
                stores.update(stores_owner.helper_writes(
                    int(call.group(1), 16), frame_sym[1], owner_function))
        # A loop gated on flag bit B reads the block that bit B built.  When two
        # decoders of one region are gated on the same bit they are two copies
        # of the same step, so the block already proven for that bit applies.
        if canonical is not None:
            block_offset, block_addresses, block = canonical
            if (block_offset == frame_sym[1]
                    and block_addresses == tuple(addresses)
                    and all(address in block for address in addresses)):
                stores = block
                record["ciphertext_source_detail"] = "same-guard-block"
        missing = [address for address in addresses if address not in stores]
        if missing:
            # The linear scan is ambiguous when two alternative blocks initialize
            # the same region on different paths; fall back to a control-flow
            # walk that records the dominating store for each address.
            reaching = stores_owner.stores_reaching(
                info["head"], {address for address in addresses},
                skip=(info["head"], info["back"]))
            stores.update(reaching)
            missing = [address for address in addresses if address not in stores]
        if missing:
            record["ciphertext_source"] = f"missing-store ({len(missing)}/{len(addresses)})"
            sites.append(record)
            continue
        ciphertext = bytes(stores[address][1] for address in addresses)
        record["stack_base_offset"] = frame_sym[1]
        if caller_addr is not None:
            record["caller_site"] = f"0x{caller_addr:x}"
        try:
            decoded, keystream, ordered = run_loop(
                dis, info, frame_sym, ciphertext, addresses, owner_function)
        except Unsupported as error:
            record["ciphertext_source"] = f"unsupported: {error}"
            sites.append(record)
            continue

        if region_shape is not None:
            guard_blocks.setdefault(region_shape, []).append(
                (frame_sym[1], tuple(addresses), stores))
        record.update({
            "ciphertext_source": "stores",
            "ciphertext_sha256": hashlib.sha256(ciphertext).hexdigest(),
            "keystream_hex": keystream.hex(),
            "decoded_hex": decoded.hex(),
            "decoded_sha256": hashlib.sha256(decoded).hexdigest(),
        })
        runs: list[dict[str, Any]] = []
        current = bytearray()
        run_start = 0
        for offset, byte in enumerate(decoded):
            if 0x20 <= byte < 0x7F:
                if not current:
                    run_start = offset
                current.append(byte)
                continue
            if len(current) >= 4:
                runs.append({"offset": run_start, "text": current.decode("ascii")})
            current = bytearray()
        if len(current) >= 4:
            runs.append({"offset": run_start, "text": current.decode("ascii")})
        record["printable_runs"] = runs
        if runs:
            longest = max(runs, key=lambda item: len(item["text"]))
            record["decoded_text"] = longest["text"]
            record["decoded_text_offset"] = longest["offset"]
        sites.append(record)
    sites.sort(key=lambda item: int(item["site"], 16))
    return {"variant": dis.name, "binary_disassembly": dis.path.name,
            "site_count": len(sites), "sites": sites}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", action="append", default=[], metavar="NAME=DISASM")
    parser.add_argument("--root", type=Path, help="analysis root holding evidence/native")
    parser.add_argument("--json-out", type=Path, required=True)
    parser.add_argument("--dump-unsupported", action="store_true")
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
    artifact = {
        "schema": "droidguard-native-string-audit-v6",
        "description": (
            "Per-loop emulation of the inlined native string decoder in the four "
            "DroidGuard native variants. Every spelling of the loop is emulated rather "
            "than pattern-matched, so the keystream and plaintext are derived from the "
            "actual instruction semantics. The buffer base is resolved path-aware "
            "through the real control flow, including caller-supplied buffers."
        ),
        "variants": [analyse(Disassembly(path, name)) for name, path in targets],
    }
    args.json_out.write_text(json.dumps(artifact, indent=2, ensure_ascii=False) + "\n")
    total = sum(item["site_count"] for item in artifact["variants"])
    resolved = sum(1 for item in artifact["variants"] for site in item["sites"]
                   if site.get("ciphertext_source") == "stores")
    decoded = sum(1 for item in artifact["variants"] for site in item["sites"] if "decoded_text" in site)
    if args.dump_unsupported:
        for item in artifact["variants"]:
            for site in item["sites"]:
                if site.get("ciphertext_source") != "stores":
                    print(item["variant"], site["site"], "|", site.get("ciphertext_source"),
                          "| base", site.get("base_register"), site.get("base_kind"),
                          "| form", site.get("address_form"))
    print(json.dumps({"total_sites": total, "resolved": resolved, "decoded_sites": decoded,
                      "unresolved": total - resolved,
                      "per_variant": {i["variant"]: i["site_count"] for i in artifact["variants"]}},
                     ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
