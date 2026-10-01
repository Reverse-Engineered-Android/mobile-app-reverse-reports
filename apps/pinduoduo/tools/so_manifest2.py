#!/usr/bin/env python3
"""Cross-reference the DEX dynamic-SO manifest against what exists on the device.

For every `SoBuildInfo` entry in com/aimi/android/common/build/a.java, report:
  - the manifest identity (epoch, md5, virtual version, component id, priority)
  - whether the ELF exists in the APK, in files/dynamic_so, or nowhere on device
  - the DEX loader call sites (`loadLibrary`, `zi2.b.H/D/Q`) that would fetch it
"""
import re, json, os, glob, collections, sys

SRC = 'jadx-out/sources'
DEX = {}
for root, _, files in os.walk(SRC):
    for fn in files:
        if fn.endswith('.java'):
            p = os.path.join(root, fn)
            DEX[p] = open(p, errors='replace').read()

man = open(os.path.join(SRC, 'com/aimi/android/common/build/a.java'), errors='replace').read()
ents = re.findall(
    r'new SoBuildInfo\("(\d+)",\s*([^,]+),\s*([^,]+),\s*"([0-9a-f]{32})",\s*"([^"]*)",\s*"([^"]+)",\s*(\d)\)',
    man)
print('SoBuildInfo entries: %d' % len(ents))

apk = {os.path.basename(p): p for p in glob.glob('unpack/lib/arm64-v8a/*.so')}
dyn = {}
for p in glob.glob('evidence/dynso/dynamic_so/lib*'):
    b = os.path.basename(p)
    m = re.match(r'^(lib.+)_\d{10,}_[0-9a-f]{32}$', b)
    if m:
        dyn[m.group(1) + '.so'] = b

rows = []
for epoch, name, ver, md5, vver, comp, pri in ents:
    nm = name.strip().strip('"')
    if nm.startswith('CmtReporter'):
        nm = 'cmtreport'
    fn = 'lib' + nm + '.so'
    where = 'APK' if fn in apk else ('device' if fn in dyn else 'absent')
    # loader call sites
    sites = []
    pat_lib = re.compile(r'loadLibrary\(\s*"%s"\s*\)' % re.escape(nm))
    pat_str = re.compile(r'"%s"' % re.escape(nm))
    for p, t in DEX.items():
        if pat_lib.search(t):
            sites.append(('loadLibrary', os.path.relpath(p, SRC)[:-5].replace('/', '.')))
        elif pat_str.search(t):
            sites.append(('dynamic_so', os.path.relpath(p, SRC)[:-5].replace('/', '.')))
    rows.append(dict(name=nm, epoch=epoch, version=ver.strip('"'), virtual_version=vver,
                     md5=md5, component=comp, priority=int(pri), where=where,
                     loader_sites=sites))

cnt = collections.Counter(r['where'] for r in rows)
print('APK    %d' % cnt['APK'])
print('device %d' % cnt['device'])
print('absent %d' % cnt['absent'])
print('\nABSENT entries and their loader call sites in the DEX:')
for r in sorted(rows, key=lambda r: r['name']):
    if r['where'] != 'absent':
        continue
    lib = [s for s in r['loader_sites'] if s[0] == 'loadLibrary']
    dso = [s for s in r['loader_sites'] if s[0] != 'loadLibrary']
    tag = 'loadLibrary=%d dynamic_so=%d' % (len(lib), len(dso))
    ex = (lib[0][1] if lib else (dso[0][1] if dso else '-'))
    print('  %-26s v%-10s pri=%d  %-24s %s' % (r['name'], r['virtual_version'], r['priority'], tag, ex))
json.dump(rows, open('evidence/so-manifest.json', 'w'), indent=1, ensure_ascii=False)
print('\nwrote evidence/so-manifest.json')
