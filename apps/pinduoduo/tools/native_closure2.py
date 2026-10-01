#!/usr/bin/env python3
"""Map every `native` method declared in the DEX to the library that provides it.

Sources scanned:
  A) APK-shipped       unpack/lib/arm64-v8a/*.so
  B) assets-embedded   unpack/assets-so/*.so        (LZMA .7z in assets/so_arm64-v8a)
  C) runtime-downloaded evidence/runtime-so/*/lib*.so  (files/dynamic_so on a live install)
Also builds the set of *customary* JNI symbol spellings (plain, `__`, `__<sig>`)
and reports which declared natives are provided by platform/system libraries
instead (libandroid.so, libwebviewchromiummeco, ...), i.e. not by the app.
"""
import re, os, glob, subprocess, collections, json, sys

SRC = 'jadx-out/sources'
decl = re.compile(
    r'\b(?:public|private|protected|static|final|synchronized|abstract|\s)*\bnative\b\s+'
    r'([\w$.\[\]<>]+)\s+(\w+)\s*\(([^)]*)\)')

methods = []
for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith('.java'): continue
        p = os.path.join(root, fn)
        rel = os.path.relpath(p, SRC)[:-5].replace('/', '.')
        try: txt = open(p, errors='replace').read()
        except Exception: continue
        pkg = ''
        m = re.search(r'^package ([\w.]+);', txt, re.M)
        if m: pkg = m.group(1) + '.'
        cls = rel if rel.startswith(pkg.rstrip('.')) or pkg == '' else pkg + rel
        if pkg and not rel.startswith(pkg):
            cls = pkg + rel
        else:
            cls = rel
        for mm in decl.finditer(txt):
            ret, name, args = mm.group(1), mm.group(2), mm.group(3)
            argc = len([a for a in args.split(',') if a.strip()])
            methods.append((cls, name, argc))
dedup = {}
for c, n, a in methods: dedup[(c, n)] = max(dedup.get((c, n), 0), a)
methods = sorted((c, n, a) for (c, n), a in dedup.items())
print('distinct native methods in DEX: %d across %d classes' % (
    len(methods), len(set(c for c, _, _ in methods))))
byclass = collections.Counter(c for c, _, _ in methods)
print('top classes:')
for c, n in byclass.most_common(25): print('   %-72s %d' % (c, n))

LIBS = ([('apk', p) for p in glob.glob('unpack/lib/arm64-v8a/*.so')] +
        [('assets', p) for p in glob.glob('unpack/assets-so/*.so')] +
        [('runtime', p) for p in glob.glob('evidence/runtime-so/*/lib*.so')] +
        [('dynamic_so', p) for p in glob.glob('evidence/dynso/dynamic_so/*/lib*.so')])

def dynsyms(lib):
    out = subprocess.run(['aarch64-linux-gnu-readelf','--dyn-syms','-W',lib],
                         capture_output=True, text=True, errors='replace').stdout
    s = set()
    for ln in out.split('\n'):
        parts = ln.split()
        if len(parts) >= 8 and parts[3] == 'FUNC' and parts[6] != 'UND':
            s.add(parts[7].split('@')[0])
    return s

exported = {}; per_lib = {}
for kind, lib in LIBS:
    b = os.path.basename(lib)
    per_lib[b] = (kind, dynsyms(lib))
    for s in per_lib[b][1]: exported.setdefault(s, []).append((kind, b))

UNI = {'_': '_1', ';': '_2', '[': '_3', '/': '_', '.': '_'}

def mangle(s):
    return ''.join(UNI.get(c, c) for c in s)

def spellings(cls, name):
    base = 'Java_' + mangle(cls) + '_' + mangle(name)
    return [base, base + '__']

rows = []
unresolved = []
for cls, name, argc in methods:
    owner = None
    for s in spellings(cls, name):
        if s in exported:
            owner = exported[s][0][0]; lib = exported[s][0][1]; sym = s; break
    if owner:
        rows.append(dict(cls=cls, method=name, argc=argc, kind=owner, lib=lib, symbol=sym))
    else:
        unresolved.append(dict(cls=cls, method=name, argc=argc))

cnt = collections.Counter(r['kind'] for r in rows)
print('\nresolved: %d  (apk=%d assets=%d runtime=%d dynamic_so=%d)   unresolved: %d' % (
    len(rows), cnt['apk'], cnt['assets'], cnt['runtime'], cnt['dynamic_so'], len(unresolved)))
print('\nresolved by library:')
for (kind, lib), n in collections.Counter((r['kind'], r['lib']) for r in rows).most_common():
    print('   %-9s %-58s %d' % (kind, lib[:58], n))
print('\nunresolved by class (top 30):')
un = collections.Counter(u['cls'] for u in unresolved)
for c, n in un.most_common(30): print('   %-72s %d' % (c, n))

json.dump(dict(resolved=rows, unresolved=unresolved,
               per_lib={k: dict(kind=v[0], exported=len(v[1])) for k, v in per_lib.items()}),
          open('evidence/native-closure.json','w'), indent=1, ensure_ascii=False)
print('\nwrote evidence/native-closure.json')
