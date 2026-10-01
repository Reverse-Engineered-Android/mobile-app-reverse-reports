#!/usr/bin/env python3
"""Decode the XOR-protected string pool shared by libpdd_secure.so and
libdyncommon.so.

Both libraries were packed by the same build-time tool and embed the SAME
8-byte key.  The key is not a literal constant in either file: it is the
one's complement of the high byte of each little-endian u64 in the keystream
table, which sits at

    libpdd_secure.so:0x198768
    libdyncommon.so :0x40f2d8

Both tables hold the identical eight qwords, so both yield

    table high bytes   0f 68 ba 1b 7c a0 2e 60
    key = ~those       f0 97 45 e4 83 5f d1 9f

Encoding model (verified byte-exactly on both libraries):

  * Ciphertext byte i, counted from the start of its own record, is
    `plaintext[i] ^ KEY[i % 8]`: the key restarts at index 0 for every string.
  * A record ends at the first raw `0x00` byte, **except** that raw `0x00` is
    real data when it sits at a key phase whose keybyte is printable ASCII
    (phase 2, `KEY[2] = 0x45 = 'E'`, or phase 5, `KEY[5] = 0x5f = '_'`)
    *and* the next byte is not itself `0x00`.  The terminator is therefore a
    single NUL in a run of NULs, not a lone NUL that happens to follow a
    printable-phase byte.

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


def harvest(path, min_len, key, names):
    """Yield (section, absolute_offset, plaintext) for each protected record."""
    with open(path, 'rb') as fh:
        data = fh.read()
    out = []
    for sec, off, size in sections(path, names):
        blob = data[off:off + size]
        i = 0
        while i < len(blob):
            text, end = decode_record(blob, i, key)
            if text is not None and len(text) >= min_len:
                out.append((sec, off + i, text))
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
    ap.add_argument('--key', default=None,
                    help='8-byte key in hex (default: derive from the file)')
    ap.add_argument('--sections', default=','.join(DEFAULT_SECTIONS))
    ap.add_argument('--count-only', action='store_true',
                    help='print one census line per file instead of the records')
    a = ap.parse_args()
    forced = bytes.fromhex(a.key) if a.key else None
    names = tuple(a.sections.split(','))
    files = []
    for x in a.inputs:
        if os.path.isdir(x):
            files += sorted(glob.glob(os.path.join(x, '**', '*.so'),
                                      recursive=True))
        else:
            files += sorted(glob.glob(x)) or [x]
    for f in sorted(set(files)):
        key = key_from_file(f, forced)
        rows = harvest(f, a.min_len, key, names)
        if not rows:
            continue
        if a.count_only:
            print(f'{os.path.basename(f):24s} records={len(rows):4d} '
                  f'key={key.hex()}')
            continue
        print(f'=== {os.path.basename(f)}  ({len(rows)} records)')
        for sec, off, s in sorted(rows, key=lambda r: r[1]):
            print(f'{sec:11s} 0x{off:08x} (mod8={off % 8}) '
                  f'{len(s):4d}  {s}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
