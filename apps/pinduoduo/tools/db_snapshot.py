#!/usr/bin/env python3
"""Read-only SQLite snapshotter that actually applies WAL/journal.

Copies each DB together with its -wal/-shm/-journal into a scratch dir, opens
it read-only (`mode=ro`), forces a WAL checkpoint so the replayed state is
materialised, and dumps: table list, full DDL, row counts, and a value-shape
aggregate per column (type + length/range buckets + null count) -- never raw
row values, so the output is safe to publish.

Usage: db_snapshot.py <src-dir> <out.txt>
"""
import os, shutil, sqlite3, sys, tempfile, re, collections

SENSITIVE_NAME_HINT = re.compile(r'uid|user|token|phone|mobile|addr|name|url|id$', re.I)

def shape(v):
    if v is None: return 'null'
    if isinstance(v, int): return 'int'
    if isinstance(v, float): return 'real'
    if isinstance(v, bytes): return 'blob(%d)' % len(v)
    s = str(v)
    if s == '': return 'empty'
    if re.fullmatch(r'-?\d+', s): return 'numstr(%d)' % len(s)
    if re.fullmatch(r'[0-9a-fA-F]{16,}', s): return 'hex(%d)' % len(s)
    if re.fullmatch(r'[A-Za-z0-9+/=_-]{24,}', s): return 'b64ish(%d)' % len(s)
    return 'text(%d)' % len(s)

def main():
    src, out = sys.argv[1], sys.argv[2]
    files = sorted(f for f in os.listdir(src) if not f.endswith(('-wal','-shm','-journal')))
    tmp = tempfile.mkdtemp(prefix='dbsnap-')
    o = open(out,'w')
    o.write('SQLite snapshot with WAL/journal replay (read-only)\n')
    o.write('values are aggregated by shape; no raw row values are emitted\n')
    o.write('='*78 + '\n')
    for f in files:
        p = os.path.join(src, f)
        if not os.path.isfile(p): continue
        with open(p,'rb') as fh: hdr = fh.read(16)
        d = os.path.join(tmp, f)
        try: shutil.copy(p, d)
        except Exception as e: o.write('SKIP %s (%s)\n' % (f,e)); continue
        for ext in ('-wal','-shm','-journal'):
            if os.path.exists(p+ext):
                try: shutil.copy(p+ext, d+ext)
                except Exception: pass
        sz = os.path.getsize(p)
        if hdr[:15] != b'SQLite format 3':
            o.write('\nDB: %s  size=%d  header=%r  (not a plaintext SQLite DB)\n' % (f, sz, hdr[:8]))
            if os.path.exists(p+'-wal'):
                o.write('   wal size=%d\n' % os.path.getsize(p+'-wal'))
            continue
        o.write('\nDB: %s  size=%d\n' % (f, sz))
        try:
            c = sqlite3.connect('file:%s?mode=ro' % d, uri=True)
            c.execute('PRAGMA journal_mode')
            try: c.execute('PRAGMA wal_checkpoint(TRUNCATE)')
            except Exception: pass
            o.write('  journal_mode=%s  page_size=%s  encoding=%s\n' % (
                c.execute('pragma journal_mode').fetchone()[0],
                c.execute('pragma page_size').fetchone()[0],
                c.execute('pragma encoding').fetchone()[0]))
            for typ, nm, sql in c.execute(
                "SELECT type,name,sql FROM sqlite_master ORDER BY type DESC, name"):
                if typ == 'table':
                    try: n = c.execute('SELECT count(*) FROM "%s"' % nm).fetchone()[0]
                    except Exception as e: n = 'ERR:%s' % e
                    o.write('\n  TABLE %-32s rows=%s\n' % (nm, n))
                    if sql: o.write('   DDL %s\n' % ' '.join(sql.split()))
                    try:
                        cols = [r[1] for r in c.execute('PRAGMA table_info("%s")' % nm)]
                    except Exception: cols = []
                    if cols and isinstance(n, int) and n > 0:
                        agg = {}
                        for col in cols:
                            counts = collections.Counter()
                            q = 'SELECT "%s" FROM "%s"' % (col.replace('"','""'), nm)
                            for (v,) in c.execute(q):
                                counts[shape(v)] += 1
                            base = counts.most_common(1)[0][0] if counts else '-'
                            agg[col] = (base, len(counts))
                        o.write('   COLUMN SHAPES (dominant, #distinct-shapes):\n')
                        for col,(b,k) in agg.items():
                            o.write('     %-28s %-16s (%d)\n' % (col, b, k))
                elif typ == 'index':
                    o.write('\n  INDEX %s\n' % nm)
                    if sql: o.write('   DDL %s\n' % ' '.join(sql.split()))
            c.close()
        except Exception as e:
            o.write('  ERROR opening: %s\n' % e)
    o.close()
    shutil.rmtree(tmp, ignore_errors=True)
    print('wrote', out)

if __name__ == '__main__':
    main()
