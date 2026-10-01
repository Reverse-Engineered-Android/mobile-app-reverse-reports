#!/usr/bin/env python3
"""Classify the obfuscation transform used in each native library.

Detects, on the real .text disassembly:
  A) OLLVM control-flow flattening: `mov w,#lo` / `movk w,#hi,lsl#16` state
     constants compared by cmp + compare chains ending in csel/br.
  B) Indirect-branch dispatcher blocks: csel -> ldr xN,[xM,xN] -> and/add/sub/eor
     -> br xN   (OLLVM -irobf-indbr / -fla dispatch)
  C) ADR+RET return-address indirection: `adr x30, <self>` ; `add x30, x30, xN` ;
     `ret`  (dynamic return target).  Followed by inline DATA decoded as
     instructions (`.inst 0x...`).
  D) data-in-.text: density of `.inst` bytes.
Basis is a plain (non-baseline) disassembly of the whole .text.
"""
import re, subprocess, sys, json, collections

def dis(path):
    txt = subprocess.run(['aarch64-linux-gnu-objdump','-d','--no-show-raw-insn',path],
                         capture_output=True, text=True, errors='replace').stdout
    ins=[]
    for ln in txt.splitlines():
        m=re.match(r'^\s*([0-9a-f]+):\s+(\S+)\s*(.*)$', ln)
        if not m or ln.strip().startswith('//'): continue
        ins.append((int(m.group(1),16), m.group(2), m.group(3).split('//')[0].strip()))
    return ins

def profile(path):
    ins = dis(path)
    n = len(ins)
    r = collections.Counter()
    adr_ret = 0; indbr = 0; state_machine = 0; inst_bytes = 0
    for k,(a,op,args) in enumerate(ins):
        r[op] += 1
        if op == '.inst': inst_bytes += 4
        if op == 'ret' and k>0 and ins[k-1][1] == 'add' and k>1 and ins[k-2][1] == 'adr' \
           and re.match(r'x30,', ins[k-2][2]) and re.match(r'x30, x30, x\d+$', ins[k-1][2]):
            adr_ret += 1
        if op == 'br':
            win = ins[max(0,k-9):k]
            ops = set(x[1] for x in win)
            if 'ldr' in ops and 'eor' in ops and ops & {'add','sub'}: indbr += 1
        if op == 'movk' and 'lsl #16' in args and k>=1 and ins[k-1][1]=='mov' and k+2<n \
           and any(ins[j][1] in ('cmp','subs','cmp') for j in range(k+1,min(k+4,n))):
            state_machine += 1
    return dict(path=path.split('/')[-1], insns=n,
        br=r['br'], blr=r['blr'], b=r['b'], bcond=sum(r[o] for o in
            ('b.eq','b.ne','b.lt','b.gt','b.le','b.ge','b.hi','b.ls','b.cs','b.cc','b.mi','b.pl','b.vs','b.vc')),
        csel=r['csel'], csinc=r['csinc'], cset=r['cset'], cneg=r['cneg'],
        movk=r['movk'], adr=r['adr'], adrp=r['adrp'], ret=r['ret'],
        inst=r['.inst'], adr_ret_indirection=adr_ret, indirect_br_dispatch=indbr,
        cff_state_constants=state_machine, data_in_text_bytes=inst_bytes,
        ratios=dict(indbr_per_k=n and round(indbr/n*1000,2),
                    data_in_text_pct=n and round(inst_bytes*100/(n*4),2),
                    adr_ret_per_k=n and round(adr_ret/n*1000,2)))

if __name__ == '__main__':
    out = []
    for p in sys.argv[2:]:
        out.append(profile(p))
    json.dump(out, open(sys.argv[1],'w'), indent=1)
    print('%-58s %8s %7s %7s %8s %7s %6s %s' % ('lib','insns','indbr','adrret','cffstate','.inst%','csel','verdict'))
    for e in out:
        v=[]
        if e['indirect_br_dispatch'] > 20: v.append('IND-BR')
        if e['adr_ret_indirection'] > 20: v.append('ADR-RET')
        if e['data_in_text_bytes'] and e['ratios']['data_in_text_pct']>3: v.append('DATA-IN-TEXT')
        if e['cff_state_constants'] > 20: v.append('FLA')
        if e['csel'] and e['csel']/max(1,e['insns'])>0.03: v.append('CSEL-HEAVY')
        print('%-58s %8d %7d %7d %8d %7.2f %6d %s' % (e['path'][:58], e['insns'],
            e['indirect_br_dispatch'], e['adr_ret_indirection'], e['cff_state_constants'],
            e['ratios']['data_in_text_pct'], e['csel'], ' '.join(v) or '-'))
