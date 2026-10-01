#!/usr/bin/env python3
"""Per-library obfuscation / crypto profile for every .so that ships with or is
downloaded by com.xunmeng.pinduoduo.  Static only: reads the ELF, disassembles
.text with aarch64-linux-gnu-objdump (or a precomputed .dis), and reports:

  * ELF shape (stripped?, sections, exported symbols, JNI-shaped exports)
  * printable-string inventory + which strings look like JNI registration names
  * known crypto constant tables (AES/SHA/MD5/Base64/SM4/ChaCha/...)
  * OLLVM control-flow-flattening indicators: ratio of constant materialisations,
    compare-chain density, indirect `br` dispatch density, `csel` density
  * RegisterNatives usage

Usage: obfscan.py <out.json> <file.so|precompiled.dis> [...]
"""
import json, os, re, struct, subprocess, sys, collections

CRYPTO = {
 'AES S-box': bytes.fromhex('637c777bf26b6fc53001672bfed7ab76'),
 'AES InvS-box': bytes.fromhex('52096ad53036a538bf40a39e81f3d7fb'),
 'SM4 S-box': bytes.fromhex('d690e9fecce13db716b61309e8a9cf74'),
 'ChaCha20 sigma': b'expand 32-byte k',
 'SHA256 H0 (be)': struct.pack('>8I',0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19),
 'SHA256 H0 (le)': struct.pack('<8I',0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19),
 'SHA256 K': struct.pack('>8I',0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5),
 'SHA512 H0 (be)': struct.pack('>8Q',0x6a09e667f3bcc908,0xbb67ae8584caa73b,0x3c6ef372fe94f82b,0xa54ff53a5f1d36f1,0x510e527fade682d1,0x9b05688c2b3e6c1f,0x1f83d9abfb41bd6b,0x5be0cd19137e2179),
 'SHA1 H0 (be)': struct.pack('>5I',0x67452301,0xefcdab89,0x98badcfe,0x10325476,0xc3d2e1f0),
 'MD5/SHA1 IV (le)': struct.pack('<4I',0x67452301,0xefcdab89,0x98badcfe,0x10325476),
 'MD5 T': struct.pack('<4I',0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee),
 'SM3 IV (be)': struct.pack('>8I',0x7380166f,0x4914b2b9,0x172442d7,0xda8a0600,0xa96f30bc,0x163138aa,0xe38dee4d,0xb0fb0e4e),
 'Base64 std': b'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/',
 'Base64 urlsafe': b'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_',
 'Poly1305 r': bytes.fromhex('0ffffffc0ffffffc0ffffffc0fffffff'),
 'CRC32 (le)': struct.pack('<I',0xedb88320),
 'CRC32C (le)': struct.pack('<I',0x82f63b78),
 'zlib deflate magic': b'\x78\x9c',
 'zstd magic': bytes.fromhex('28b52ffd'),
 'lzma magic': bytes.fromhex('5d00008000'),
 'P-256 p': bytes.fromhex('ffffffff00000001000000000000000000000000ffffffffffffffffffffffff'),
 'secp256k1 p': bytes.fromhex('fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f'),
 'Curve25519 p': struct.pack('<Q',0x7fffffffffffffed),
}

COND = set('b.eq b.ne b.cs b.hs b.cc b.lo b.mi b.pl b.vs b.vc b.hi b.ls b.ge b.lt b.gt b.le b.al'.split())
CMPOPS = {'cmp','cmn','tst','ccmp','ccmn','fcmp'}
STATEOPS = {'mov','movz','movk','movn','orr','add','sub','and','eor'}

def sections(d):
    """Minimal ELF64 section + symbol reader (little endian, AArch64)."""
    if d[:4] != b'\x7fELF' or d[4] != 2: return None
    e_shoff, = struct.unpack_from('<Q', d, 0x28)
    e_shentsize, e_shnum, e_shstrndx = struct.unpack_from('<HHH', d, 0x3a)
    secs = []
    for i in range(e_shnum):
        off = e_shoff + i*e_shentsize
        nm, typ, flags, addr, offset, size, link, info, align, entsize = struct.unpack_from('<IIQQQQIIQQ', d, off)
        secs.append(dict(name_off=nm, type=typ, flags=flags, addr=addr, offset=offset, size=size, link=link, entsize=entsize))
    shstr = secs[e_shstrndx]
    base = shstr['offset']
    for s in secs:
        e = d.index(b'\0', base + s['name_off'])
        s['name'] = d[base + s['name_off']:e].decode('utf-8', 'replace')
    return secs

def symtab(d, secs):
    names = {}; dynsyms = 0; syms = []
    for s in secs:
        if s['type'] not in (2, 11): continue  # SYMTAB / DYNSYM
        strs = next((x for x in secs if x['name'] == '.strtab' or x['name'] == '.dynstr'), None)
        strsec = None
        for x in secs:
            if x['type'] == 3:  # STRTAB
                if s['name'] == '.dynsym' and x['name'] == '.dynstr': strsec = x
                if s['name'] == '.symtab' and x['name'] == '.strtab': strsec = x
        if strsec is None: continue
        n = s['size'] // 24
        for i in range(n):
            o = s['offset'] + i*24
            st_name, st_info, st_other, st_shndx, st_value, st_size = struct.unpack_from('<IBBHQQ', d, o)
            if st_name == 0: continue
            e = d.index(b'\0', strsec['offset'] + st_name)
            nm = d[strsec['offset'] + st_name:e].decode('utf-8', 'replace')
            if s['name'] == '.dynsym': dynsyms += 1
            syms.append(dict(name=nm, value=st_value, size=st_size, info=st_info, shndx=st_shndx, tab=s['name']))
    return syms, dynsyms

def strings(d, minlen=6):
    return [m.group().decode('latin1') for m in re.finditer(rb'[\x20-\x7e]{%d,}' % minlen, d)]

def disasm(path, so_path):
    """Return list of (addr, mnemonic, operands) using objdump."""
    if path.endswith('.dis'):
        txt = open(path, errors='replace').read()
    else:
        txt = subprocess.run(['aarch64-linux-gnu-objdump','-d','--no-show-raw-insn',path],
                             capture_output=True, text=True, errors='replace').stdout
    out = []
    for ln in txt.splitlines():
        m = re.match(r'^\s*([0-9a-f]+):\s+(\S+)\s*(.*)$', ln)
        if m and not ln.strip().startswith('//'):
            out.append((int(m.group(1),16), m.group(2), m.group(3).split('//')[0].strip()))
    return out

def cff_profile(ins):
    n = len(ins)
    if not n: return {}
    const32 = collections.Counter(); big = 0
    cmps = set(); zeros = 0
    br = 0; blr = 0; bcond = 0; b = 0; csel = 0; ret = 0; ldr = 0; str_ = 0
    movchain = 0; calls = 0
    for a, op, args in ins:
        if op in ('mov','movz','movn','movk'):
            m = re.match(r'(w|x)\d+, #(0x[0-9a-f]+|\d+)(, lsl #(\d+))?$', args)
            if m:
                v = int(m.group(2),0)
                if v == 0: zeros += 1
                if v > 0xffff:
                    const32[v] += 1
                    if v <= 0xffffffff: big += 1
        if op in CMPOPS:
            m = re.search(r'#(0x[0-9a-f]+|\d+)$', args)
            if m: cmps.add(int(m.group(1),0))
        if op == 'br': br += 1
        if op == 'blr': blr += 1; calls += 1
        if op in COND: bcond += 1
        if op == 'b': b += 1
        if op == 'csel': csel += 1
        if op == 'ret': ret += 1
        if op.startswith('ldr'): ldr += 1
        if op.startswith('str'): str_ += 1
    return dict(insns=n, movz_big=big, distinct_big=len(const32), top_big=const32.most_common(5),
                cmp_imm_distinct=len(cmps), br=br, blr=blr, b=b, bcond=bcond, csel=csel,
                ret=ret, ldr=ldr, str=str_, zero_mov=zeros)

def main():
    out_path = sys.argv[1]
    targets = sys.argv[2:]
    res = []
    for t in targets:
        rec = {'path': os.path.basename(t)}
        if t.endswith('.dis'):
            d = b''; rec['kind'] = 'precompiled-dis'
            rec['dis_lines'] = sum(1 for _ in open(t, errors='replace'))
            ins = disasm(t, None)
            rec['cff'] = cff_profile(ins)
            res.append(rec); continue
        d = open(t,'rb').read()
        rec['size'] = len(d)
        rec['sha256'] = __import__('hashlib').sha256(d).hexdigest()
        secs = sections(d) or []
        rec['sections'] = {s['name']: s['size'] for s in secs}
        rec['has_symtab'] = any(s['name']=='.symtab' for s in secs)
        syms, dys = symtab(d, secs)
        exports = [s['name'] for s in syms if s['name'].startswith('Java_')]
        jni_reg = [s['name'] for s in syms if 'RegisterNatives' in s['name']]
        rec['dynsym_count'] = dys
        rec['sym_count'] = len(syms)
        rec['jni_export_count'] = len(exports)
        rec['jni_exports'] = sorted(exports)
        rec['has_symtab_entries'] = len([s for s in syms if s['tab']=='.symtab'])
        strs = strings(d)
        rec['string_count'] = len(strs)
        rec['crypto'] = sorted(k for k,v in CRYPTO.items() if v in d)
        rec['register_natives'] = bool(jni_reg) or (b'RegisterNatives' in d)
        rec['has_ollvm_marker'] = bool(re.search(rb'ollvm|Obfuscator-LLVM|fla|bcf|sub_', d[:200000]))
        # .text disassembly
        text = next((s for s in secs if s['name']=='.text'), None)
        if text and len(d) < 40*1024*1024:
            tmp = '/tmp/_obf_%s' % os.path.basename(t)
            open(tmp,'wb').write(d)
            ins = disasm(tmp, t)
            rec['cff'] = cff_profile(ins)
            os.unlink(tmp)
        res.append(rec)
    json.dump(res, open(out_path,'w'), indent=1, ensure_ascii=False)
    print(json.dumps(res, indent=1, ensure_ascii=False)[:2000])

if __name__ == '__main__':
    main()
