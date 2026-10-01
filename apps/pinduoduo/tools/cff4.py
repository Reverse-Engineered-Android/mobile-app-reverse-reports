#!/usr/bin/env python3
"""Corrected per-export CFF profile for libpdd_secure.so.

The earlier cff3.py bounded each export by the *next export address*, which for
the final export (SecureNative.b @0x37294) ran to end of file and produced a
wildly inflated 344k-instruction window.  This version determines each
function's real extent by scanning for the first function epilogue
(`ldp x29, x30, [sp, ...]` ... `ret`, or `add sp, sp, #N` ... `ret`).
"""
import re, collections, json, sys

ins = []
for ln in open('libpdd_secure.dis', errors='replace'):
    m = re.match(r'^\s*([0-9a-f]+):\s+[0-9a-f]{8}\s+(\S+)\s*(.*)$', ln)
    if m:
        ins.append((int(m.group(1), 16), m.group(2), m.group(3).split('//')[0].strip()))
idx = {a: i for i, (a, _, _) in enumerate(ins)}

JNI = {'sb':0xfc9c,'ng2':0xfd50,'ng':0x17234,'eca':0x17680,'egv':0x17dac,'hf':0x184e4,
 'dcc':0x18698,'ne':0x1883b0,'re':0x189b8,'mhk':0x18e510,'aew':0x19ff4,'ae':0x1a60c,
 'adw':0x1cb60,'ad':0x1edf0,'gvv':0x1f720,'dv':0x1f75c,'ea':0x1ff58,'ecb':0x25654,
 'ecn':0x25ddc,'eb4':0x25fec,'glk':0x26114,'rs':0x2652c,'cr':0x265b8,'cps':0x26634,
 'ale':0x28638,'alm':0x286e8,'sdr':0x28798,'gal':0x28a54,'itst':0x28b10,'itst2':0x290e0,
 'enc':0x29414,'dec':0x2a060,'s':0x36278,'b':0x37294}

def function_end(start):
    """First `ret` that is preceded within 8 insns by a stack-restore."""
    i = idx[start]
    for k in range(i, min(i + 60000, len(ins) - 1)):
        if ins[k][1] == 'ret':
            win = ins[max(0, k - 8):k]
            if any(x[1] == 'add' and x[2].startswith('sp, sp, #') for x in win) or \
               any(x[1] == 'ldp' and x[2].startswith('x29, x30, [sp') for x in win):
                return ins[k][0] + 4
    return ins[min(i + 60000, len(ins) - 1)][0]

order = sorted(JNI.items(), key=lambda kv: kv[1])
rows = []
for name, start in order:
    end = function_end(start)
    body = [x for x in ins if start <= x[0] < end]
    st = collections.Counter()
    cmpimm = set(); br = 0; csel = 0; bcond = 0; b = 0; blr = 0; big = 0
    for a, op, ar in body:
        st[op] += 1
        if op == 'cmp':
            m = re.match(r'w\d+, #(0x[0-9a-f]+|\d+)$', ar)
            if m: cmpimm.add(int(m.group(1), 0))
        if op == 'br': br += 1
        if op == 'blr': blr += 1
        if op == 'b': b += 1
        if op == 'csel': csel += 1
        if re.match(r'^b\.', op): bcond += 1
        if op == 'movk' and 'lsl #16' in ar: big += 1
    rows.append(dict(export=name, start='%#x' % start, end='%#x' % end,
                     insns=len(body), cmp_imm_distinct=len(cmpimm), br=br,
                     blr=blr, b=b, bcond=bcond, csel=csel, movk16=big,
                     state_stores=sum(1 for a, op, ar in body
                                      if op == 'str' and re.match(r'w\d+, \[sp, #0x14\]$', ar))))
rows.sort(key=lambda r: -r['insns'])
json.dump(rows, open('evidence/cff-profile.json', 'w'), indent=1)
print('%-6s %-9s %-9s %8s %8s %7s %7s %7s %8s' % (
    'export','start','end','insns','cmpImm','br','csel','bcond','movk16'))
for r in rows:
    print('%-6s %-9s %-9s %8d %8d %7d %7d %7d %8d' % (
        r['export'], r['start'], r['end'], r['insns'], r['cmp_imm_distinct'],
        r['br'], r['csel'], r['bcond'], r['movk16']))
print()
print('total exports %d, total insns in exports %d' % (len(rows), sum(r['insns'] for r in rows)))
