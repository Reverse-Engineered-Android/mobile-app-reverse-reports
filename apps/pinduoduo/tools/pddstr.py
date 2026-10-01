#!/usr/bin/env python3
"""Decode the XOR-protected string pool inside libpdd_secure.so.

`libpdd_secure.so` keeps a string pool in .rodata whose plaintext is XORed
with an 8-byte repeating key.  `FindClass` for the `DeviceNative` class and
every reflective class/method name used by `SecureNative` live there, so the
class descriptor is **not** absent from the library -- it is protected.

Layout:  [8-byte XOR mask][NUL-terminated body], body XORed with
         KEY[(absolute_file_offset + i) % 8]

KEY is not stored in plaintext.  It is the one's complement of the high byte
of each little-endian u64 in the keystream table at 0x198768:

    0x198768 qwords -> high bytes 0f 68 ba 1b 7c a0 2e 60
    KEY = ~those bytes                  = f0 97 45 e4 83 5f d1 9f

The decoder routines in the library reduce to this one formula: the `eor`
variants (0xbba00, 0xbbe94, 0xbc378, 0xbc4e0, 0xbc648) use the table
directly, while the `eon` variants (0x34880 and friends) compute the
complement, which is the same value.

Read-only: only reads bytes from the file given on the command line.
"""
import argparse
import re
import struct
import sys

DEFAULT_POOL = 0x1928c0
DEFAULT_TABLE = 0x198768
DEFAULT_END = 0x198600
PRINTABLE = re.compile(rb'[\x20-\x7e]{4,}')
# Characters that actually occur in this pool's plaintexts.  Anything else
# means we are looking at shifted filler, not a real record.
CLEAN = set('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
            '0123456789/_$.;()[]<>-%: ,@!*+=')
# Records are English identifiers; inter-record filler decodes to short
# uppercase runs, so requiring lowercase letters rejects it.
MIN_LOWER = 2
# Shifted filler shows up as 'E' followed by a run of lowercase letters
# (a mis-decoded JS-BS marker plus the shifted remainder).  Real records
# never look like that.
ARTIFACT = re.compile(r'^E[a-z]{4,}$')
TOKEN = re.compile(r'^[A-Za-z][A-Za-z0-9/_$.;()\[\]<>%-]{2,}$')
IDENT_CHARS = set('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/_$.;()[]<>%-')


def keystream(blob, table):
    """One's complement of the high byte of 8 consecutive u64s."""
    raw = bytes(blob[table + 8 * i + 3] for i in range(8))
    return bytes(0xff ^ b for b in raw)


def decode(blob, start, end, key, phase=0):
    """XOR `key` over [start,end); `phase` rotates the key."""
    return bytes(blob[start + i] ^ key[(start + i + phase) % 8]
                 for i in range(max(0, end - start)))


def harvest(blob, start, end, key, min_lower=MIN_LOWER, drop_artifacts=True):
    """Yield (offset, phase, plaintext) for the pool's real string records.

    Every record was encrypted starting from *some* point in the 8-byte
    keystream, and the phase is per-record rather than a function of the
    absolute offset: the class name at 0x1928c0 (offset %% 8 == 0) uses
    phase 0, but `fiddler` at 0x195214 (offset %% 8 == 4) also uses phase 0,
    not 4.  So all 8 phases must be tried and the plausible decoding kept.

    Inter-record filler decodes to short, vowel-free uppercase runs under
    every phase, so a lowercase-density test rejects it regardless of phase.
    """
    best = {}
    for phase in range(8):
        text = decode(blob, start, end, key, phase)
        for m in PRINTABLE.finditer(text):
            raw = m.group().decode('ascii')
            if not all(c in CLEAN for c in raw):
                continue
            if sum(1 for c in raw if c.islower()) < min_lower:
                continue
            if drop_artifacts and ARTIFACT.match(raw):
                continue
            off = start + m.start()
            prev = best.get(off)
            if prev is None or len(raw) > len(prev[1]):
                best[off] = (phase, raw)
    for off in sorted(best):
        phase, raw = best[off]
        yield off, phase, raw


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('lib', help='path to libpdd_secure.so')
    ap.add_argument('--pool', type=lambda s: int(s, 0), default=DEFAULT_POOL,
                    help=f'pool base offset (default {DEFAULT_POOL:#x})')
    ap.add_argument('--table', type=lambda s: int(s, 0), default=DEFAULT_TABLE,
                    help=f'keystream table offset (default {DEFAULT_TABLE:#x})')
    ap.add_argument('--end', type=lambda s: int(s, 0), default=DEFAULT_END,
                    help=f'scan end offset (default {DEFAULT_END:#x}); the pool is followed by the keystream table')
    ap.add_argument('--min-lower', type=int, default=2,
                    help='minimum lowercase letters in a candidate (default 2); '
                         'raise it to drop noise, lower it to catch short names')
    ap.add_argument('--all', action='store_true',
                    help='print every printable run, including filler')
    args = ap.parse_args()

    blob = open(args.lib, 'rb').read()
    key = keystream(blob, args.table)
    if args.table + 64 > len(blob):
        sys.exit('keystream table lies outside the file')
    print(f'key = {key.hex()}  (from table {args.table:#x})', file=sys.stderr)

    seen, n = set(), 0
    for off, phase, s in harvest(blob, args.pool, min(args.end, len(blob)), key,
                         0 if args.all else args.min_lower,
                         not args.all):
        if s in seen and len(s) < 14:
            continue
        seen.add(s)
        n += 1
        print(f'{off:#08x}  ph{phase}  {s}')
    print(f'\n{n} strings', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
