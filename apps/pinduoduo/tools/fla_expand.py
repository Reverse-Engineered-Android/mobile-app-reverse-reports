#!/usr/bin/env python3
"""Expand every FLA dispatcher found by fla_census.py into its block chain.

For each core:
  1. find the dispatcher extent (FDE covering the br site, else heuristic),
  2. abstract-interpret the prologue to recover the stack-resident jump table,
  3. walk the state machine with concrete condition evaluation.

The result answers "how many of the 116 are actually resolvable", which is the
honest gate for claiming "no unexplained obfuscated code".
"""
import re, struct, sys, os, json
from pathlib import Path
from collections import defaultdict

M32=0xFFFFFFFF; M64=0xFFFFFFFFFFFFFFFF
INSN_RE=re.compile(r"^\s*([0-9a-f]+):\t(?:[0-9a-f ]{8,}\t)?(.*)$")
IMM_RE=re.compile(r"#(-?0x[0-9a-f]+|-?\d+)")
COND={"lt":lambda a,b:a<b,"le":lambda a,b:a<=b,"gt":lambda a,b:a>b,"ge":lambda a,b:a>=b,
      "eq":lambda a,b:a==b,"ne":lambda a,b:a!=b,"hi":lambda a,b:a>b,"ls":lambda a,b:a<=b,
      "hs":lambda a,b:a>=b,"cc":lambda a,b:a<b,"cs":lambda a,b:a>=b}

def parse(path):
    out={}
    for line in Path(path).read_text(errors="replace").splitlines():
        m=INSN_RE.match(line)
        if not m: continue
        a=int(m.group(1),16)
        t=m.group(2).split("//")[0].split("<")[0].strip()
        if t: out[a]=re.sub(r"\s+"," ",t)
    return out

def imm(s):
    m=IMM_RE.search(s)
    if not m: return None
    t=m.group(1)
    return int(t,16) if t.lstrip("-").startswith("0x") else int(t)

def norm(r):
    r=r.strip()
    if len(r)>1 and r[0]=="w" and r[1:].isdigit(): return "x"+r[1:]
    return r

def load_fdes(path):
    if not os.path.exists(path): return []
    out=[]
    for line in Path(path).read_text().splitlines():
        p=line.split()
        if len(p)==2:
            try: out.append((int(p[0]),int(p[1])))
            except ValueError: pass
    return out

class St:
    def __init__(self):
        self.r={}; self.mem={}    # mem: byte offset -> set of 64-bit values
        self.sp={}
    def rd(self,n):
        v=self.r.get(norm(n),set())
        return {x&M32 for x in v} if n.startswith("w") else set(v)
    def wr(self,n,vals):
        if n.startswith("w"): vals={v&M32 for v in vals}
        n=norm(n)
        if not vals: self.r.pop(n,None); return
        self.r[n]=set(vals)
    def sread(self,off,size,signed=False):
        """Read `size`-byte value at byte offset `off`."""
        out=set()
        for base in self.mem.get(off,set()):
            if size==4:
                out.add(base & M32)
            elif size==8:
                out.add(base & M64)
        return out
    def swrite(self,off,size,vals):
        self.mem[off]=set(vals)
        # For str/stp: a later wider read at the same address should see the value
        # zero-extended (or sign-extended for 32->64); a later narrower read sees
        # the low bits.  Since our values are non-negative integers, low-bit
        # masking on read handles this.
        for s2 in (4,8):
            if s2!=size and off+s2 in self.mem:
                pass
    def clr(self):
        for n in list(self.r):
            if len(self.r[n])>1: self.r.pop(n,None)

def interpret(insns, start, end, maxins=100000):
    """Return (steps, table_info) for one FLA core."""
    st=St(); pc=start; steps=[]; guard=None; n=0; seen_jumps=set()
    while pc in insns and n<maxins:
        n+=1
        text=insns[pc]; op,_,rest=text.partition(" ")
        ops=[o.strip() for o in rest.split(",")] if rest else []
        nxt=pc+4
        if op=="adrp":
            m=re.search(r",\s*([0-9a-f]+)\s*$",rest)
            if m: st.wr(ops[0],{int(m.group(1),16)})
        elif op=="add" and len(ops)==3:
            rd,ra,rb=ops; v=imm(rb)
            if v is not None:
                if ra=="sp": st.sp[rd]=v
                else: st.wr(rd,{x+v for x in st.rd(ra)})
            else:
                a,b=st.rd(ra),st.rd(rb)
                if a and b: st.wr(rd,{x+y for x in a for y in b})
        elif op=="sub" and len(ops)==3:
            rd,ra,rb=ops; v=imm(rb)
            if v is not None: st.wr(rd,{x-v for x in st.rd(ra)})
            else:
                a,b=st.rd(ra),st.rd(rb)
                if a and b: st.wr(rd,{x-y for x in a for y in b})
        elif op in ("mov","movz") and len(ops)==2:
            v=imm(ops[1])
            if v is not None:
                m=re.search(r"lsl #(\d+)",rest); sh=int(m.group(1)) if m else 0
                st.wr(ops[0],{v<<sh})
            elif ops[1] in ("wzr","xzr"): st.wr(ops[0],{0})
        elif op=="movk" and len(ops)>=2:
            v=imm(ops[1]); m=re.search(r"lsl #(\d+)",rest); sh=int(m.group(1)) if m else 0
            if v is not None:
                prior=st.rd(ops[0]) or {0}
                st.wr(ops[0],{(p&~(0xFFFF<<sh))|((v&0xFFFF)<<sh) for p in prior})
        elif op=="cmp": guard=(sorted(st.rd(ops[0])),sorted(st.rd(ops[1])),rest)
        elif op=="csel" and len(ops)==4:
            a,b=st.rd(ops[1]),st.rd(ops[2]); took=None
            if guard and len(guard[0])==1 and len(guard[1])==1:
                fn=COND.get(ops[3])
                if fn is not None: took=fn(guard[0][0],guard[1][0])
            steps.append({"pc":pc,"cond":ops[3],"guard":guard[2] if guard else None,
                          "else":sorted(a),"taken":sorted(b),"resolved":took})
            st.wr(ops[0], b if took is True else (a if took is False else a|b))
        elif op=="cinc" and len(ops)==3:
            a=st.rd(ops[1]); took=None
            fn=COND.get(ops[2])
            if guard and fn is not None and len(guard[0])==1 and len(guard[1])==1:
                took=fn(guard[0][0],guard[1][0])
            st.wr(ops[0], a if took else {x+1 for x in a})
        elif op=="eor" and len(ops)==3:
            a,b=st.rd(ops[1]),st.rd(ops[2])
            if a and b: st.wr(ops[0],{x^y for x in a for y in b})
        elif op=="str":
            m=re.match(r"^(\w+),\s*\[sp,\s*#(-?\d+)\]$",rest)
            if m:
                size=4 if m.group(1).startswith("w") else 8
                st.swrite(int(m.group(2)),size,st.rd(m.group(1)))
        elif op=="stp":
            m=re.match(r"^(\w+),\s*(\w+),\s*\[sp,\s*#(-?\d+)\]$",rest)
            if m:
                off=int(m.group(3))
                for k,nm in enumerate((m.group(1),m.group(2))):
                    size=4 if nm.startswith("w") else 8
                    st.swrite(off+4*k,size,st.rd(nm))
        elif op=="ldr":
            m=re.match(r"^(\w+),\s*\[sp,\s*#(-?\d+)\]$",rest)
            if m:
                off=int(m.group(2)); size=4 if m.group(1).startswith("w") else 8
                st.wr(m.group(1),st.sread(off,size))
            else:
                m=re.search(r"\[(\w+),\s*(\w+),\s*sxtw #3\]",rest)
                if m:
                    rd,rp,ri=ops[0],m.group(1),m.group(2)
                    base=st.sp.get(rp); vals=set()
                    if base is not None:
                        for idx in st.rd(ri):
                            vals|=st.sread(base+8*idx,8)
                    st.wr(rd,vals)
        elif op=="ldp":
            m=re.match(r"^(\w+),\s*(\w+),\s*\[sp,\s*#(-?\d+)\]$",rest)
            if m:
                off=int(m.group(3))
                for k,nm in enumerate((m.group(1),m.group(2))):
                    size=4 if nm.startswith("w") else 8
                    st.wr(nm,st.sread(off+4*k,size))
        elif op=="b":
            m=re.search(r"([0-9a-f]+)\s*$",rest)
            if m: nxt=int(m.group(1),16)
        elif op=="bl":
            for i in range(19): st.r.pop("x%d"%i,None)
        elif op=="ret":
            break
        elif op=="br":
            targets=st.rd(rest)
            steps.append({"br":pc,"targets":sorted(targets),"entry":sorted(st.rd(rest))})
            if len(targets)!=1: break
            pc=next(iter(targets)); st.clr(); guard=None; continue
        pc=nxt
    return steps,n

def distinct_marker(x): return None

if __name__=="__main__":
    import argparse
    ap=argparse.ArgumentParser()
    ap.add_argument("objdump")
    ap.add_argument("--fdes", default="/tmp/fdes.txt")
    ap.add_argument("--cores", default="all", help="comma-separated hex br sites, or all")
    ap.add_argument("--maxins", type=int, default=20000)
    args=ap.parse_args()

    insns=parse(args.objdump)
    addrs=sorted(insns)

    # core boundaries: nearest ret or next FDE start after the br
    fdes=load_fdes(args.fdes)
    fde_starts=sorted(a for a,b in fdes)

    def find_start(br):
        # walk back to the nearest `ret`; the dispatcher's prologue is between it
        # and the first dispatch head
        for i in range(len(addrs)-1,-1,-1):
            a = addrs[i]
            if a >= br: continue
            if a < br - 0x4000: break
            if insns[a] == "ret": return a + 4
        return br - 0x1000
    def find_end(br):
        # the FDE end that contains br
        for a,b in fdes:
            if a<=br<b: return b
        for a in addrs:
            if a<=br: continue
            if a>br+0x4000: break
            if insns[a]=="ret": return a+4
        return br+0x2000

    # census (reuse logic inline)
    INSN_RE=insns; pos={a:i for i,a in enumerate(addrs)}
    cores=[]
    for a in addrs:
        t=insns[a]
        if not (t.startswith("ldr x") and "sxtw #3" in t): continue
        dest=t.split(",")[0].split()[1]
        i=pos[a]; br=None
        for j in range(i+1,min(i+20,len(addrs))):
            if addrs[j]>a+0x40: break
            if insns[addrs[j]]=="br %s"%dest: br=addrs[j]; break
        if br is None: continue
        try: idxreg=t.split("[")[1].split(",")[1].strip()
        except IndexError: continue
        state_slot=None
        for j in range(max(0,i-24),i):
            m=re.match(r"^ldr (w\d+), \[sp, #(-?\d+)\]$", insns[addrs[j]])
            if m and m.group(1)==idxreg: state_slot=int(m.group(2))
        xor_fed=any(
            (m:=re.match(r"^eor (w\d+), (w\d+), (w\d+)$", insns[addrs[j]]))
            and m.group(1)==idxreg
            for j in range(max(0,i-24),i))
        wb=False
        if state_slot is not None:
            pat="[sp, #%d]"%state_slot
            wb=any(insns[addrs[j]].startswith("str w") and pat in insns[addrs[j]]
                   for j in range(i+1,min(i+20,len(addrs))) if addrs[j]<=br)
        if state_slot is not None and xor_fed and wb:
            cores.append({"br":br,"load":a,"slot":state_slot})

    print("# FLA cores: %d"%len(cores))
    print()
    results=[]
    for c in cores:
        end=find_end(c["br"])
        try:
            start=find_start(c["br"])
            steps,n=interpret(insns,start,end,maxins=args.maxins)
        except Exception as ex:
            results.append(dict(c,start=hex(find_start(c["br"])),end=hex(find_end(c["br"])),err=str(ex)))
            continue
        # count resolved csel branches
        cs=[s for s in steps if "cond" in s]
        brs=[s for s in steps if "br" in s]
        ncsel=len(cs); nres=sum(1 for s in cs if s.get("resolved") is not None)
        nbr=len(brs); namb=sum(1 for s in brs if len(s.get("targets",[]))>1)
        results.append(dict(c,start=start,end=end,insns=n,
                            csel=ncsel,resolved=nres,ambig=namb))

    # summary
    ok=[r for r in results if r.get("csel",0)>0 and r.get("resolved",0)==r.get("csel",0)]
    part=[r for r in results if r.get("csel",0)>0 and r.get("resolved",0)<r.get("csel",0)]
    err=[r for r in results if "err" in r]
    print("| br | start | end | insns | csel | resolved | ambiguous | status |")
    print("| --- | --- | --- | ---: | ---: | ---: | ---: | --- |")
    for r in results:
        if "err" in r:
            print("| `%#x` | `%s` | `%s` | - | - | - | - | ERROR: %s |"%(
                r["br"],r["start"],r["end"],r["err"][:40]))
            continue
        s="OK" if r.get("csel",0)>0 and r.get("resolved",0)==r.get("csel",0) else "PARTIAL"
        print("| `%#x` | `%#x` | `%#x` | %s | %s | %s | %s | %s |"%(
            r["br"],r["start"],r["end"],r.get("insns","-"),r.get("csel","-"),
            r.get("resolved","-"),r.get("ambig","-"),s))
    print()
    print("total %d  ok %d  partial %d  error %d"%(len(results),len(ok),len(part),len(err)))
