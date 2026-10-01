#!/usr/bin/env python3
"""Enumerate genuine RegisterNatives / UnregisterNatives call sites in AArch64 ELFs.

Why this exists
---------------
`RegisterNatives` on ARM64 goes through the `_JNIEnv` function table
(`(*env)->RegisterNatives(...)`), so:

  * there is no imported symbol to grep for, and
  * there is no literal "RegisterNatives" string in .rodata.

Grepping either way yields a false negative.  The table index is the only
signal available:

  #1720 = 215*8 = _JNIEnv::RegisterNatives
  #1728 = 216*8 = _JNIEnv::UnregisterNatives

The index alone is NOT sufficient.  Three different instruction patterns use
the same displacement, and only the first is a registration:

  (a) a real JNI table call -- the env pointer is a value that was *loaded*
      (it arrives as a function argument), and the loaded entry is the callee:
        ldr x8, [x19]            ; x19 = the JNIEnv pointer
        ldr x8, [x8, #1720]
        blr x8

  (b) a PLT stub, which lives in the `.plt` section and walks the `.got.plt`
      slot table with the same stride:
        adrp x16, <GOT page>
        ldr  x17, [x16, #1720]
        add  x16, x16, #0x6b8
        br   x17
      This alone adds one bogus "registration" per imported symbol -- in
      `libaudio_engine.so` it would report a RegisterNatives/UnregisterNatives
      pair for every JNI entry point the library imports.

  (c) this app's own obfuscated dispatcher, same as (b) but the loaded value
      is a *base* that gets a packed offset added before the call:
        adrp x8, <static table page>
        ldr  x8, [x8, #1720]
        add  x8, x8, x9          ; base + packed offset
        blr  x8

Patterns (b) and (c) are ubiquitous in PDD's control-flow flattening and, if
counted, inflate the census with sites that register nothing (pattern (b) alone
adds one bogus "registration" per imported symbol).  Two independent checks are
therefore applied, and a site must pass both:

  1. the site must not sit inside the `.plt` section (section ranges are read
     from the ELF header, so this does not depend on objdump's symbol labels,
     which fall back to `@@Base+offset` for the tail of `.plt`); and
  2. the entry must reach the indirect branch **unmodified** -- any arithmetic
     (`add`/`sub`/`and`/`orr`/`eor`/`mvn`/`mov`/`movk`/shift) on it between the
     table load and the branch means a dispatcher, not a registration.

The base-register test (was it `ldr`'d or `adrp`'d) is deliberately *not* used:
`.text` code that legitimately keeps the JNIEnv pointer in a callee-saved
register writes it once at function entry and then reuses it, so a backward
scan over the window finds the `ldr` that installed it only by luck.  Relying
on it silently drops real registrations -- `libpcrash_anr.so:0x43a4` and
`libpdd_j2v8.so`'s 26 sites among them.

Read-only: disassembles ELF files, never executes or loads them.
"""
import argparse
import glob
import os
import re
import subprocess
import sys

IDX = {'1720': 'RegisterNatives', '1728': 'UnregisterNatives'}
MODIFIES = re.compile(r'(add|sub|and|orr|eor|mvn|mov|movk|lsl|lsr|asr|extr|'
                      r'bic|orn|eon|sbfx|ubfx)\b')


def instructions(path):
    objdump = os.environ.get('OBJDUMP', 'aarch64-linux-gnu-objdump')
    out = subprocess.run([objdump, '-d', '--no-show-raw-insn', path],
                         capture_output=True, text=True).stdout
    funcs, cur = {}, None
    for line in out.splitlines():
        m = re.match(r'^([0-9a-f]+) <(.+)>:$', line)
        if m:
            cur = None if '@plt' in m.group(2) else m.group(2)
            if cur:
                funcs.setdefault(cur, [])
            continue
        m = re.match(r'^\s*([0-9a-f]+):\s+(\S+)\s*(.*)$', line)
        if m and cur is not None:
            funcs[cur].append((int(m.group(1), 16), m.group(2),
                               m.group(3).split('//')[0].strip()))
    return funcs


def plt_range(path):
    """Return (start, end) of the .plt section, or None."""
    out = subprocess.run(['readelf', '-SW', path], capture_output=True,
                         text=True).stdout
    for ln in out.splitlines():
        m = re.match(r'\s*\[\s*\d+\]\s+\.plt\s+PROGBITS\s+([0-9a-f]+)\s+'
                     r'([0-9a-f]+)\s+([0-9a-f]+)', ln)
        if m:
            start = int(m.group(1), 16)
            return start, start + int(m.group(3), 16)
    return None


# Any of these ends the basic block, so the search must stop: a branch found
# past one is unrelated to the table load we started from.  Without this the
# window happily walks into the next block and matches a `br x8` that has
# nothing to do with `#1720` -- which is exactly how a plain data load
# (`ldr x8,[x8,#1728]` / `strb w0,[x8]`) gets miscounted as a registration.
BLOCK_END = re.compile(r'^(b|br|blr|bl|ret|cbz|cbnz|tbz|tbnz|brk|hlt)\b')


def scan(path, window=24, skip_plt=True):
    hits = []
    plt = plt_range(path) if skip_plt else None
    for fname, body in instructions(path).items():
        for i, (addr, op, args) in enumerate(body):
            m = re.match(r'(x\d+), \[(x\d+), #(1720|1728)\]$', args)
            if op != 'ldr' or not m:
                continue
            vreg, base, off = m.group(1), m.group(2), m.group(3)
            if plt and plt[0] <= addr < plt[1]:
                hits.append((fname, addr, IDX[off], 'plt'))
                continue
            for o2, r2 in ((o, r) for _, o, r in body[i + 1:i + 1 + window]):
                if o2 in ('blr', 'br') and r2.strip() == vreg:
                    hits.append((fname, addr, IDX[off], 'direct'))
                    break
                if BLOCK_END.match(o2):
                    break
                if vreg in r2 and MODIFIES.search(o2):
                    hits.append((fname, addr, IDX[off], 'dispatcher'))
                    break
    return hits


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('inputs', nargs='+', help='ELF files, globs or directories')
    ap.add_argument('--all', action='store_true',
                    help='also report dispatcher false positives')
    a = ap.parse_args()
    files = []
    for x in a.inputs:
        if os.path.isdir(x):
            files += sorted(glob.glob(os.path.join(x, '**', '*.so'),
                                      recursive=True))
        else:
            files += sorted(glob.glob(x)) or [x]
    tot_r = tot_u = libs = 0
    for f in sorted(set(files)):
        hits = scan(f)
        real = [h for h in hits if h[3] == 'direct']
        if not real and not a.all:
            continue
        if not real:
            continue
        libs += 1
        n = sum(1 for h in real if h[2] == 'RegisterNatives')
        u = len(real) - n
        tot_r += n
        tot_u += u
        print(f'{os.path.basename(f):24s} R={n:2d} U={u:2d}   ' +
              ' '.join(f'0x{h[1]:x}:{h[2][:3]}' for h in real))
        if a.all:
            for h in hits:
                if h[3] == 'dispatcher':
                    print(f'    (skipped {h[3]}) 0x{h[1]:x} {h[2]}')
    print(f'\nlibs={libs}  RegisterNatives={tot_r}  UnregisterNatives={tot_u}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
