#!/usr/bin/env python3
"""Decode the XOR-protected string pool shared by libpdd_secure.so and
libdyncommon.so.

Both libraries were packed by the same build-time tool and embed the SAME
eight-qword keystream table, which sits at

    libpdd_secure.so:0x198768
    libdyncommon.so :0x40f2d8

The key is not a literal constant: it is one byte-row of that table, optionally
complemented.  `.text` contains exactly four such loops, differing only in the
shift (which byte of each qword) and the operator (`eor` = plain, `eon` =
complemented):

    eon w13, w13, w14, lsr #24   ->  mask A  f0 97 45 e4 83 5f d1 9f
    eor w13, w13, w14, lsr #24   ->  mask B  0f 68 ba 1b 7c a0 2e 60
    eon w13, w13, w14, lsr #8    ->  mask C  b1 30 15 3d f6 99 23 83
    eor w13, w13, w14, lsr #8    ->  mask D  4e cf ea c2 09 66 dc 7c

A and B are bitwise complements of each other, as are C and D, matching the
paired `eor`/`eon` loops.  Which mask a given record uses is a property of the
*call site* that consumes it, not of the record: A-group and C-group records sit
interleaved in the same `.rodata`.  A census that applies one mask therefore
silently drops the other three groups -- this tool defaults to the union.

Earlier revisions of this tool shipped only mask A and reported "151 / 129
records"; those numbers were an artefact of that single-mask scan.

Encoding model (verified byte-exactly on both libraries):

  * Ciphertext byte i, counted from the start of its own record, is
    `plaintext[i] ^ KEY[i % 8]`: the key restarts at index 0 for every string.
  * A record ends at the first raw `0x00` byte, **except** that raw `0x00` is
    real data when it sits at a key phase whose keybyte is printable ASCII
    *and* the next byte is not itself `0x00`.  The terminator is therefore a
    single NUL in a run of NULs, not a lone NUL that happens to follow a
    printable-phase byte.  (For mask A the printable phases are 2, `0x45 =
    'E'`, and 5, `0x5f = '_'`; for mask B they are 2, `0x68 = 'h'`, and 5,
    `0xa0` -- not printable, which is why B splits on a plain NUL.)

That exception is not a detail -- it is the whole difficulty of this pool.
Because `plaintext ^ KEY` is zero exactly when `plaintext == KEY[phase]`,
every `E` falling on phase 2 and every `_` falling on phase 5 is stored as a
raw `0x00`, i.e. **inside** the record, indistinguishable from a terminator
unless the phase is taken into account.  Concretely:

  * `ab_secure_hook_detect_7020` -- the `_` before `detect` lands on phase 5;
  * `Java_com_xunmeng_...._SecureNative_aesEncryptAddress` -- several `_`;
  * a record ending in `_` (`..._encryptNetBook_`) stores `00 00 00`: the
    trailing underscore, then the record terminator, then padding.

Splitting the section on a single `0x00` therefore truncates roughly every
other identifier; splitting on `0x00 0x00` additionally eats a trailing `_`.
Decoding sequentially and stopping only at a raw `0x00` whose phase is
neither 2 nor 5 recovers the plaintext exactly.

A consequence worth stating explicitly, because it has been mis-described:
the phase is *not* a function of the record's absolute file offset.  Records
that begin at offset % 8 == 0 and at offset % 8 == 4 both use phase 0.  A
decoder that anchors the key to the absolute offset therefore recovers only
the subset of records that happen to start 8-byte aligned, and a decoder that
tries all eight alignments over a whole section recovers that subset plus a
large amount of mis-aligned noise.

libdyncommon is selective: benign strings stay in plaintext right next to
encrypted ones in the same section, so one section legitimately contains both.

Read-only: reads the file only, never executes or loads it.
"""
import argparse
import glob
import os
import re
import struct
import subprocess
import sys

KEY = bytes([0xf0, 0x97, 0x45, 0xe4, 0x83, 0x5f, 0xd1, 0x9f])
DEFAULT_SECTIONS = ('.rodata', '.data.rel.ro')
KEYTABLES = {'libpdd_secure.so': 0x198768, 'libdyncommon.so': 0x40f2d8}

# Four effective masks are in use, all derived from the same 8-qword keystream
# table but with a different shift (which byte of each qword) and a different
# operator.  The decode loops in .text are the evidence:
#
#   eon w13, w13, w14, lsr #24   ->  mask A  (one's complement of the high byte)
#   eor w13, w13, w14, lsr #24   ->  mask B  (the high byte itself)
#   eon w13, w13, w14, lsr #8    ->  mask C
#   eor w13, w13, w14, lsr #8    ->  mask D
#
# A and B were recovered first; C and D were recovered from records that A and
# B leave as noise.  Each mask is confirmed independently by >= 4 records, so
# none of them is a single-string fit.  Which record is consumed by which loop
# is a property of the *call site*, not of the record, so a census that uses
# one mask silently drops the records belonging to the other loops: report the
# union.
MASKS = {
    'A': bytes([0xf0, 0x97, 0x45, 0xe4, 0x83, 0x5f, 0xd1, 0x9f]),
    'B': bytes([0x0f, 0x68, 0xba, 0x1b, 0x7c, 0xa0, 0x2e, 0x60]),
    'C': bytes([0xb1, 0x30, 0x15, 0x3d, 0xf6, 0x99, 0x23, 0x83]),
    'D': bytes([0x4e, 0xcf, 0xea, 0xc2, 0x09, 0x66, 0xdc, 0x7c]),
}


def sections(path, names=DEFAULT_SECTIONS):
    out = subprocess.run(['readelf', '-SW', path], capture_output=True,
                         text=True).stdout
    spans = []
    for ln in out.splitlines():
        m = re.match(r'\s*\[\s*\d+\]\s+(\S+)\s+PROGBITS\s+[0-9a-f]+\s+'
                     r'([0-9a-f]+)\s+([0-9a-f]+)', ln)
        if m and m.group(1) in names:
            spans.append((m.group(1), int(m.group(2), 16), int(m.group(3), 16)))
    return spans


def key_from_file(path, key=None):
    """Derive the key from the file's own keystream table when possible."""
    if key is not None:
        return key
    name = os.path.basename(path)
    if name not in KEYTABLES:
        return KEY
    with open(path, 'rb') as fh:
        fh.seek(KEYTABLES[name])
        raw = fh.read(64)
    return bytes(0xff ^ raw[8 * i + 3] for i in range(8))


MAX_RECORD = 1024


def decode_record(blob, start, key):
    """Decode the record beginning at `start`.

    Stops at the first raw 0x00 whose key phase is not a printable keybyte
    (see the module docstring: raw 0x00 is real data at those phases).
    Returns (plaintext, end_offset) or (None, None) if this is not a record.
    """
    out = bytearray()
    i = 0
    while i < MAX_RECORD:
        pos = start + i
        if pos >= len(blob):
            return None, None
        raw = blob[pos]
        phase = i % len(key)
        if raw == 0x00:
            nxt = blob[pos + 1] if pos + 1 < len(blob) else 0x00
            if not (0x20 <= key[phase] < 0x7f and nxt != 0x00):
                break
        out.append(raw ^ key[phase])
        i += 1
    text = bytes(out)
    if not text or not re.fullmatch(rb'[\x20-\x7e]+', text):
        return None, None
    return text.decode('ascii'), start + i


def harvest(path, min_len, keys, names):
    """Yield (section, absolute_offset, plaintext, mask) per protected record.

    `keys` is a mapping mask-letter -> 8-byte key.  Scanning resumes at the end
    of each accepted record, so a record is attributed to exactly one mask; the
    ordering of `keys` therefore decides ties, and the union over all masks is
    what a census should count.
    """
    with open(path, 'rb') as fh:
        data = fh.read()
    out = []
    for sec, off, size in sections(path, names):
        blob = data[off:off + size]
        i = 0
        while i < len(blob):
            hit = None
            for name, key in keys.items():
                text, end = decode_record(blob, i, key)
                if text is not None and len(text) >= min_len:
                    hit = (name, text, end)
                    break
            if hit:
                name, text, end = hit
                out.append((sec, off + i, text, name))
                i = end
                while i < len(blob) and blob[i] == 0:
                    i += 1
            else:
                i += 1
    return out


def main():
    ap = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('inputs', nargs='+', help='ELF files, globs or directories')
    ap.add_argument('--min-len', type=int, default=8,
                    help='minimum decoded record length (default 8)')
    ap.add_argument('--key', default=None, metavar='HEX',
                    help='single 8-byte key; overrides --mask')
    ap.add_argument('--mask', default=None, metavar='A|B|C|D',
                    help='use one recovered mask (default: all four, union)')
    ap.add_argument('--sections', default=','.join(DEFAULT_SECTIONS))
    ap.add_argument('--count-only', action='store_true',
                    help='print one census line per file instead of the records')
    a = ap.parse_args()
    names = tuple(a.sections.split(','))
    forced = None
    if a.key:
        forced = {'forced': bytes.fromhex(a.key)}
    elif a.mask:
        forced = {a.mask.upper(): MASKS[a.mask.upper()]}
    files = []
    for x in a.inputs:
        if os.path.isdir(x):
            files += sorted(glob.glob(os.path.join(x, '**', '*.so'),
                                      recursive=True))
        else:
            files += sorted(glob.glob(x)) or [x]
    for f in sorted(set(files)):
        if forced is not None:
            keys = forced
        else:
            base = key_from_file(f)
            keys = {n: base if n == 'A' else k for n, k in MASKS.items()}
            keys['A'] = base
        rows = harvest(f, a.min_len, keys, names)
        if not rows:
            continue
        if a.count_only:
            per = {n: sum(1 for r in rows if r[3] == n) for n in sorted(keys)}
            detail = ' '.join(f'{n}={per[n]}' for n in sorted(keys))
            print(f'{os.path.basename(f):24s} union={len(rows):4d} {detail}')
            continue
        print(f'=== {os.path.basename(f)}  ({len(rows)} records)')
        for sec, off, s, name in sorted(rows, key=lambda r: r[1]):
            print(f'{sec:11s} 0x{off:08x} (mod8={off % 8}) [{name}] '
                  f'{len(s):4d}  {s}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
