#!/usr/bin/env python3
"""Dump the apmobilesecuritysdk AD/AL code -> collector mapping from jadx output.

The Alipay device-fingerprint SDK registers ~41 short codes (`AD1`..`AD42`,
plus `AL3`) in a single `map.put` block; the actual collectors live in
`com.alipay.b.a.a.b.b`.  Rebuilding the code->method pairing by hand is
error-prone, so this prints it verbatim from the decompiled source.

Read-only: parses .java files produced by jadx; needs no device access.
"""
import argparse
import os
import re
import sys

PUT = re.compile(r'map\.put\("(A[D-L]\d+)"\s*,\s*(.*?)\)\s*;')
CALL = re.compile(r'com\.alipay\.b\.a\.a\.b\.b\.([a-zA-Z])\((.*?)\)')
METHOD = re.compile(
    r'public static [^\n{]*?\b([a-zA-Z])\(([^)]*)\)[^\n{]*\{')
STRING = re.compile(r'"([^"\n]{3,70})"')
INTERESTING = re.compile(
    r'permission|/proc|/sys|/dev|/system|/data|ro\.|android\.|SERIAL|'
    r'Settings|elapsed|TimeZone|Locale|Sensor|qemu|goldfish|generic|'
    r'keyguard|airplane|battery|BATTERY|wlan|gsm\.|Taint|fingerprint|'
    r'build\.prop|cpuinfo|meminfo|NetworkInterface|operator|baseband')


def methods(path):
    src = open(path, encoding='utf-8', errors='replace').read()
    marks = [(m.start(), m.group(1)) for m in METHOD.finditer(src)]
    out = {}
    for k, (pos, name) in enumerate(marks):
        end = marks[k + 1][0] if k + 1 < len(marks) else len(src)
        body = src[pos:end]
        keys = sorted({c for c in STRING.findall(body) if INTERESTING.search(c)})
        ctx = 'Context' in src[pos:pos + 90]
        out.setdefault(name, []).append((ctx, keys))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('sources', help='jadx sources root')
    args = ap.parse_args()

    dc = os.path.join(args.sources, 'com/alipay/apmobilesecuritysdk/d/c.java')
    bb = os.path.join(args.sources, 'com/alipay/b/a/a/b/b.java')
    for p in (dc, bb):
        if not os.path.isfile(p):
            sys.exit(f'missing {p}')

    impl = methods(bb)
    src = open(dc, encoding='utf-8', errors='replace').read()
    print(f'{"code":6s} {"collector":34s} semantics')
    for code, rhs in PUT.findall(src):
        m = CALL.search(rhs)
        if m:
            fn, argn = m.group(1), m.group(2)
            variants = impl.get(fn, [])
            pick = None
            for ctx, keys in variants:
                if ('context' in argn) == ctx:
                    pick = keys
                    break
            pick = pick if pick is not None else (variants[0][1] if variants else [])
            target = f'b.{fn}({argn})'
            print(f'{code:6s} {target:34s} {", ".join(pick) if pick else "-"}')
        else:
            print(f'{code:6s} {"(local / constant)":34s} {rhs.strip()}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
