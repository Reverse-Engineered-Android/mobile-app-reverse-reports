#!/usr/bin/env python3
"""Un-flatten an OLLVM control-flow-flattened function back into a plain CFG.

The pattern OLLVM's `-fla` emits is:

    <disp>: ldr  wS, [sp, #STATE]
            cmp  wS, wC1 / b.<cc> B1        <- binary-search over the state space
            cmp  wS, wC2 / b.<cc> B2
            ...
            b    <disp>                      <- "no match" = re-dispatch
    ...
    <Bi>:   ... real code ...
            mov wK, #lo / movk wK, #hi,lsl#16
            str wK, [sp, #STATE]
            b    <disp>                      <- back edge

This tool resolves the state constants (they are frequently materialised with
`mov`/`movk` into a scratch register instead of an immediate `cmp`), groups the
instructions into blocks by dispatcher target, and prints the recovered CFG:

    state --enters--> block --writes state--> ...

Exit condition used: the dispatcher tree is exhaustive, so every reachable
state maps to exactly one block, and every block ends by writing the state of
its successor(s).  A function that satisfies this has no residual opaque
control flow.
"""
import re, sys, json, collections

def load(path):
    ins=[]
    for ln in open(path, errors='replace'):
        m=re.match(r'^\s*([0-9a-f]+):\s+(?:[0-9a-f]{8}\s+)?(\S+)\s*(.*)$', ln)
        if m and not ln.strip().startswith('//'):
            ins.append((int(m.group(1),16), m.group(2), m.group(3).split('//')[0].strip()))
    return ins

COND = re.compile(r'^b\.(eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)$')

def resolve_consts(body):
    """Forward constant propagation over straight-line runs."""
    regs = {}
    out = {}
    for a,op,ar in body:
        if op in ('mov','movz'):
            m=re.match(r'w(\d+), #(0x[0-9a-f]+|\d+)(?:, lsl #(\d+))?$', ar)
            if m:
                regs['w'+m.group(1)] = int(m.group(2),0) << (int(m.group(3)) if m.group(3) else 0)
                continue
            m=re.match(r'w(\d+), (w\d+)$', ar)
            if m: regs['w'+m.group(1)] = regs.get(m.group(2)); continue
        if op=='movk':
            m=re.match(r'w(\d+), #(0x[0-9a-f]+|\d+), lsl #(\d+)$', ar)
            if m:
                k='w'+m.group(1); prev=regs.get(k) or 0
                regs[k] = (prev & ~(0xffff << int(m.group(3)))) | (int(m.group(2),0) << int(m.group(3)))
                continue
        if op in ('add','sub','orr','and','eor'):
            m=re.match(r'w(\d+), (w\d+), #(0x[0-9a-f]+|\d+)$', ar)
            if m and op in ('add','sub'):
                src=regs.get(m.group(2)); 
                if src is not None:
                    v=int(m.group(3),0)
                    regs['w'+m.group(1)] = src+v if op=='add' else src-v
                    continue
        if op in ('cmp','cmn'):
            m=re.match(r'w(\d+), #(0x[0-9a-f]+|\d+)$', ar)
            if m: out[a]=int(m.group(2),0); continue
            m=re.match(r'w(\d+), (w\d+)$', ar)
            if m:
                v=regs.get(m.group(2))
                if v is not None and 0 <= v <= 0xffffffff: out[a]=v
                continue
        if op in ('ldr','str','bl','blr','ret','b','br'):
            if op=='str': pass
            else: regs={}
        if COND.match(op):
            regs={}
    return out

def unflatten(ins, lo, hi, label, want_states=None):
    body=[x for x in ins if lo<=x[0]<hi]
    idx={a:i for i,(a,_,_) in enumerate(body)}
    back=collections.Counter()
    for a,op,ar in body:
        if op=='b':
            m=re.match(r'^([0-9a-f]+)', ar)
            if m and int(m.group(1),16)<a: back[int(m.group(1),16)]+=1
    if not back: return None
    head=back.most_common(1)[0][0]
    # state slot: the first [sp,#x] loaded at the head
    hd=idx[head]; slot=None
    for j in range(hd, min(hd+6,len(body))):
        if body[j][1]=='ldr' and (not body[j][2].startswith('x')):
            m=re.match(r'w\d+, \[sp, #(0x[0-9a-f]+|\d+)\]$', body[j][2])
            if m: slot=int(m.group(1),0); break
    consts=resolve_consts(body)
    # dispatch leaves: cmp (resolved) followed by b.<cc> to somewhere != head
    leaves={}
    for (a,v) in sorted(consts.items()):
        i=idx.get(a)
        if i is None: continue
        for j in range(i+1, min(i+3,len(body))):
            op=body[j][1]
            if COND.match(op):
                m=re.match(r'^([0-9a-f]+)', body[j][2])
                if m:
                    t=int(m.group(1),16)
                    if t!=head: leaves.setdefault(v,[]).append((bcond(op),t))
                break
            if op not in ('ccmp','csel','b'): break
    # state writes: str wR,[sp,#slot] with wR materialised just before
    writes=collections.defaultdict(list)
    for k,(a,op,ar) in enumerate(body):
        if op!='str': continue
        m=re.match(r'w(\d+), \[sp, #(0x[0-9a-f]+|\d+)\]$', ar)
        if not m or int(m.group(2),0)!=slot: continue
        # look backwards for the constant assigned to that register
        for j in range(k-1, max(-1,k-16), -1):
            if body[j][1]=='mov':
                pm=re.match(r'w%s, #(0x[0-9a-f]+|\d+)$' % m.group(1), body[j][2])
                if pm: writes[a].append(int(pm.group(1),0)); break
            if body[j][1]=='movk':
                pmk=re.match(r'w%s, #(0x[0-9a-f]+|\d+), lsl #(\d+)$' % m.group(1), body[j][2])
                if pmk:
                    hiv=int(pmk.group(1),0)<<int(pmk.group(2))
                    for jj in range(j-1, max(-1,j-6), -1):
                        if body[jj][1]=='mov':
                            pm2=re.match(r'w%s, #(0x[0-9a-f]+|\d+)$' % m.group(1), body[jj][2])
                            if pm2: writes[a].append(int(pm2.group(1),0)|hiv); break
                    break
            if body[j][1] in ('ldr','str','bl','blr'): break
    # block boundaries = union of dispatcher leaf targets and state-write sites
    targets=sorted(set(t for _,ts in leaves.items() for _,t in ts))
    bounds=[]
    for t in targets:
        nxt=min([x for x in (targets+[hi]) if x>t], default=hi)
        bounds.append((t,nxt))
    blocks=[]
    for (t,nxt) in bounds:
        blk=[x for x in body if t<=x[0]<nxt]
        w=[v for a in writes for v in writes[a] if t<=a<nxt]
        blocks.append(dict(addr=hex(t),end=hex(nxt),insns=len(blk),
                           writes=sorted(set(w))))
    return dict(function=label, dispatcher=hex(head), state_slot=hex(slot),
                insns=len(body),
                dispatch_states=sorted(leaves.keys()),
                state_to_block={hex(k):[hex(t) for _,t in v] for k,v in leaves.items()},
                blocks=blocks,
                blocks_writing_state=len([b for b in blocks if b['writes']]),
                states_written=sorted(set(w for b in blocks for w in b['writes'])))

def bcond(op): return op

if __name__=='__main__':
    ins=load(sys.argv[1]); lo,hi=int(sys.argv[2],16),int(sys.argv[3],16)
    r=unflatten(ins,lo,hi,sys.argv[4] if len(sys.argv)>4 else 'fn')
    if r is None: print('no flattened dispatcher found'); sys.exit(1)
    if len(sys.argv)>5: json.dump(r,open(sys.argv[5],'w'),indent=1,ensure_ascii=False)
    print(json.dumps({k:v for k,v in r.items() if k!='blocks'}, indent=1, ensure_ascii=False))
    print('blocks:', len(r['blocks']))
    for b in r['blocks'][:40]: print('  ', b)
