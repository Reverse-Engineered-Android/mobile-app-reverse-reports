#!/usr/bin/env python3
import argparse
import collections
import hashlib
import json
import math
from pathlib import Path


MAGIC = b"DICE-AM\0"
PAGE_SIZE = 4096
CHUNK_SIZE = 8192


def page_summary(data: bytes, page_number: int):
    page = data[page_number * PAGE_SIZE : (page_number + 1) * PAGE_SIZE]
    frequencies = collections.Counter(page)
    entropy = -sum(
        (count / len(page)) * math.log2(count / len(page))
        for count in frequencies.values()
    )
    return {
        "page_number": page_number,
        "offset": page_number * PAGE_SIZE,
        "first_16_hex": page[:16].hex(),
        "nonzero_bytes": sum(value != 0 for value in page),
        "entropy_bits_per_byte": round(entropy, 3),
        "tail_8_hex": page[-8:].hex(),
    }


def inspect(path: Path, sample_pages: int):
    data = path.read_bytes()
    if data[:8] != MAGIC:
        raise ValueError(f"unexpected magic: {data[:8]!r}")
    if len(data) < PAGE_SIZE or len(data) % PAGE_SIZE:
        raise ValueError("DICE size is not a multiple of 4096")

    chunk_count = int.from_bytes(data[20:22], "big")
    expected_size = chunk_count * CHUNK_SIZE
    page_count = len(data) // PAGE_SIZE
    nonzero_pages = 0
    printable_run_lengths = collections.Counter()
    current_run = 0
    for value in data:
        if 32 <= value <= 126:
            current_run += 1
        else:
            if current_run >= 4:
                printable_run_lengths[current_run] += 1
            current_run = 0
    if current_run >= 4:
        printable_run_lengths[current_run] += 1

    for page_number in range(page_count):
        page = data[page_number * PAGE_SIZE : (page_number + 1) * PAGE_SIZE]
        nonzero_pages += any(page)

    selected_pages = list(range(min(sample_pages, page_count)))
    if page_count > sample_pages:
        selected_pages.extend(range(max(sample_pages, page_count - sample_pages), page_count))

    return {
        "path": str(path),
        "file_size": len(data),
        "file_sha256": hashlib.sha256(data).hexdigest(),
        "magic": MAGIC.decode("ascii").rstrip("\0"),
        "page_size": PAGE_SIZE,
        "chunk_size": CHUNK_SIZE,
        "header_chunk_count": chunk_count,
        "expected_size_from_header": expected_size,
        "size_matches_header": expected_size == len(data),
        "page_count": page_count,
        "page_pair_count": page_count // 2,
        "nonzero_pages": nonzero_pages,
        "printable_run_length_counts": dict(sorted(printable_run_lengths.items())),
        "sampled_pages": [
            page_summary(data, page_number) for page_number in selected_pages
        ],
    }


def main():
    parser = argparse.ArgumentParser(
        description="Inspect AMap DICE-AM structure without printing record contents."
    )
    parser.add_argument("input", type=Path)
    parser.add_argument("--sample-pages", type=int, default=3)
    args = parser.parse_args()
    if args.sample_pages < 0:
        parser.error("--sample-pages must be non-negative")
    print(json.dumps(inspect(args.input, args.sample_pages), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
