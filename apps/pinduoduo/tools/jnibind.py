#!/usr/bin/env python3
"""Classify every DEX-declared native method by how its library binds it.

For each declared native:
  1. Java_<mangled> export exists          -> BIND=export
  2. `name` + JNI signature strings both appear in some lib's .rodata
     and that lib has JNI_OnLoad           -> BIND=registernatives
  3. neither                               -> BIND=none (library not in snapshot)
"""
import re, os, glob, subprocess, collections, json, sys

SRC = 'jadx-out/sources'
decl = re.compile(
    r'\b(?:public|private|protected|static|final|synchronized|abstract|\s)*\bnative\b\s+'
    r'([\w$.\[\]<>]+)\s+(\w+)\s*\(([^)]*)\)')
TYPE = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S',
        'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}

def sig(desc):
    """Map a Java type descriptor to a JNI type signature."""
    d = desc.strip()
    if d.endswith('[]'):
        return '[' + sig(d[:-2])
    d = re.sub(r'<.*>', '', d)
    if d in TYPE:
        return TYPE[d]
    if d == 'String':
        return 'Ljava/lang/String;'
    if d == 'Object':
        return 'Ljava/lang/Object;'
    if d == 'Class':
        return 'Ljava/lang/Class;'
    if d == 'Throwable':
        return 'Ljava/lang/Throwable;'
    if d and d[0].islower() and '.' in d:
        return 'L' + d.replace('.', '/') + ';'
    return 'L' + d.replace('.', '/') + ';'

methods = []
for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith('.java'):
            continue
        p = os.path.join(root, fn)
        rel = os.path.relpath(p, SRC)[:-5].replace('/', '.')
        txt = open(p, errors='replace').read()
        m = re.search(r'^package ([\w.]+);', txt, re.M)
        pkg = m.group(1) + '.' if m else ''
        cls = rel if (not pkg or rel.startswith(pkg.rstrip('.'))) else pkg + rel
        for mm in decl.finditer(txt):
            ret, name, args = mm.group(1), mm.group(2), mm.group(3)
            argc = len([a for a in args.split(',') if a.strip()])
            methods.append((cls, name, argc, ret, args))
dedup = {}
for c, n, a, r, ar in methods:
    dedup[(c, n)] = (max(dedup.get((c, n), (0, '', ''))[0], a), r, ar)
methods = sorted((c, n, v[0], v[1], v[2]) for (c, n), v in dedup.items())

LIBS = ([('apk', p) for p in glob.glob('unpack/lib/arm64-v8a/*.so')] +
        [('assets', p) for p in glob.glob('unpack/assets-so/*.so')] +
        [('runtime', p) for p in glob.glob('evidence/runtime-so/*/lib*.so')] +
        [('dynamic_so', p) for p in glob.glob('evidence/dynso/dynamic_so/*/lib*.so')])

libinfo = {}
for kind, lib in LIBS:
    b = os.path.basename(lib)
    out = subprocess.run(['aarch64-linux-gnu-readelf', '-W', '--dyn-syms', lib],
                         capture_output=True, text=True, errors='replace').stdout
    exp = set()
    onload = False
    for ln in out.split('\n'):
        pt = ln.split()
        if len(pt) >= 8 and pt[3] == 'FUNC' and pt[6] != 'UND':
            nm = pt[7].split('@')[0]
            exp.add(nm)
            if nm == 'JNI_OnLoad':
                onload = True
    st = subprocess.run(['strings', '-a', '-n', '3', lib], capture_output=True,
                        text=True, errors='replace').stdout.splitlines()
    libinfo[b] = dict(kind=kind, path=lib, exported=exp, onload=onload, strings=set(st))

exported = {}
for b, info in libinfo.items():
    for s in info['exported']:
        exported.setdefault(s, []).append((info['kind'], b))

UNI = {'_': '_1', ';': '_2', '[': '_3', '/': '_', '.': '_'}
def mangle(s): return ''.join(UNI.get(c, c) for c in s)

def jni_sig(ret, args):
    parts = [a.strip() for a in args.split(',') if a.strip()]
    # strip leading type params, keep the declared type
    ts = [sig(p.split()[-1]) for p in parts]
    return '(' + ''.join(ts) + ')' + sig(ret)

rows, unbound = [], []
for cls, name, argc, ret, args in methods:
    base = 'Java_' + mangle(cls) + '_' + mangle(name)
    hit = None
    for s in (base, base + '__'):
        if s in exported:
            hit = ('export',) + exported[s][0] + (s,)
            break
    if hit:
        rows.append(dict(cls=cls, method=name, argc=argc, bind='export',
                         kind=hit[1], lib=hit[2], symbol=hit[3]))
        continue
    sg = jni_sig(ret, args)
    cands = [b for b, i in libinfo.items()
             if i['onload'] and name in i['strings'] and sg in i['strings']]
    if cands:
        cands.sort(key=lambda b: (libinfo[b]['kind'] != 'apk', len(libinfo[b]['exported'])))
        rows.append(dict(cls=cls, method=name, argc=argc, bind='registernatives',
                         kind=libinfo[cands[0]]['kind'], lib=cands[0], symbol=sg))
    else:
        unbound.append(dict(cls=cls, method=name, argc=argc))

cnt = collections.Counter(r['bind'] for r in rows)
print('declared natives: %d in %d classes' % (len(methods), len(set(c for c, *_ in methods))))
print('bind=export         %d' % cnt['export'])
print('bind=registernatives %d' % cnt['registernatives'])
print('unbound             %d' % len(unbound))
print('\nregisternatives by library:')
for (k, l), n in collections.Counter((r['kind'], r['lib']) for r in rows if r['bind'] == 'registernatives').most_common():
    print('   %-9s %-50s %d' % (k, l[:50], n))
print('\nunbound by class (top 20):')
for c, n in collections.Counter(u['cls'] for u in unbound).most_common(20):
    print('   %-70s %d' % (c, n))
json.dump(dict(rows=rows, unbound=unbound,
               libs={k: dict(kind=v['kind'], onload=v['onload'], exported=len(v['exported']))
                     for k, v in libinfo.items()}),
          open('evidence/jni-bind.json', 'w'), indent=1, ensure_ascii=False)
print('\nwrote evidence/jni-bind.json')
