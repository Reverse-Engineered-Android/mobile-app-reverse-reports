#!/usr/bin/env python3
"""Enumerate real RegisterNatives / UnregisterNatives call sites in AArch64 ELFs.

Why this exists
---------------
`RegisterNatives` on ARM64 goes through the `_JNIEnv` function table
(`(*env)->RegisterNatives(...)` -> `ldr x8,[x0]` / `ldr x8,[x8,#1720]` /
`blr x8`), so:

  * there is no imported symbol to grep for, and
  * there is no literal "RegisterNatives" string in .rodata.

Grepping either way yields a false negative.  The table index is the only
reliable signal:

  #1720 = 215*8 = _JNIEnv::RegisterNatives
  #1728 = 216*8 = _JNIEnv::UnregisterNatives

False positives come from `adrp`-relative data accesses (PLT stubs, .bss
globals) that happen to use the same displacement, so the base register is
required to be a dereferenced pointer before the site is accepted.

Read-only: disassembles ELF files, never executes or loads them.
"""
import argparse
import glob
import os
import re
import subprocess
import sys

IDX = {1720: 'RegisterNatives', 1728: 'UnregisterNatives'}
DATA_PAGES = ('1d8000', '1cf000', '1d9000')


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


def scan(path):
    hits = []
    for fname, body in instructions(path).items():
        data_regs = set()
        for i, (addr, op, args) in enumerate(body):
            m = re.match(r'x(\d+), (\S+)$', args)
            if op == 'adrp' and m and m.group(2) in DATA_PAGES:
                data_regs.add(m.group(1))
            m = re.match(r'x(\d+), \[x(\d+), #(1720|1728)\]$', args)
            if op != 'ldr' or not m:
                continue
            vreg, base, off = m.group(1), m.group(2), int(m.group(3))
            pointer = False
            for _, o2, r2 in reversed(body[max(0, i - 40):i]):
                if o2 == 'adrp' and re.match(rf'x{base}, ', r2 or ''):
                    pointer = base not in data_regs
                    break
                if o2 == 'ldr' and re.match(rf'x{base}, \[', r2 or ''):
                    pointer = True
                    break
            if not pointer:
                continue
            for _, o2, r2 in body[i + 1:i + 20]:
                if o2 in ('blr', 'br') and r2.strip() == f'x{vreg}':
                    hits.append((fname, addr, IDX[off]))
                    break
    return hits


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('libs', nargs='+', help='ELF files or globs/directories')
    args = ap.parse_args()
    paths = []
    for a in args.libs:
        if os.path.isdir(a):
            paths += glob.glob(os.path.join(a, '**', '*.so'), recursive=True)
        else:
            paths += sorted(glob.glob(a)) or [a]
    reg = unreg = 0
    for p in sorted(set(paths)):
        hits = scan(p)
        if not hits:
            continue
        print(os.path.basename(p))
        for fname, addr, kind in hits:
            print(f'   {addr:#x} {kind} in {fname}')
            if kind == 'RegisterNatives':
                reg += 1
            else:
                unreg += 1
    print(f'\nRegisterNatives: {reg}  UnregisterNatives: {unreg}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
