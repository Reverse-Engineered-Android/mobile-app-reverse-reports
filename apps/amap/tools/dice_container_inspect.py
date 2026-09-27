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
DIRECTORY_TYPE = 0x05
DIRECTORY_COUNT_OFFSET = 4
DIRECTORY_TABLE_OFFSET = 12
DIRECTORY_CELL_SIZE = 12
HEADER_SIZE_XOR = 0xdefe
HEADER_BYTE_XORS = (0xAB, 0x01, 0x89)


def directory_summary(data: bytes, page_number: int):
    pair_number = page_number // 2
    pair_start = pair_number * CHUNK_SIZE
    pair = data[pair_start : pair_start + CHUNK_SIZE]
    directory = data[page_number * PAGE_SIZE : (page_number + 1) * PAGE_SIZE]
    record_count = directory[DIRECTORY_COUNT_OFFSET]
    table_end = DIRECTORY_TABLE_OFFSET + record_count * 2
    offsets = []
    if table_end <= PAGE_SIZE:
        offsets = [
            int.from_bytes(directory[offset : offset + 2], "big")
            for offset in range(DIRECTORY_TABLE_OFFSET, table_end, 2)
        ]

    spans = []
    prefixes = []
    for offset in offsets:
        cell_end = offset + DIRECTORY_CELL_SIZE
        spans.append((offset, cell_end))
        if 0 <= offset and cell_end <= len(pair):
            prefixes.append(pair[offset + 4 : offset + 12])

    span_valid = (
        table_end <= PAGE_SIZE
        and all(0 <= start and end <= CHUNK_SIZE for start, end in spans)
    )
    sorted_spans = sorted(spans)
    spans_overlap = any(
        previous_end > next_start
        for (_, previous_end), (next_start, _) in zip(sorted_spans, sorted_spans[1:])
    )
    prefixes_sorted = len(prefixes) == record_count and all(
        previous <= current for previous, current in zip(prefixes, prefixes[1:])
    )
    return {
        "page_number": page_number,
        "pair_number": pair_number,
        "record_count": record_count,
        "table_end": table_end,
        "entry_offset_min": min(offsets) if offsets else None,
        "entry_offset_max": max(offsets) if offsets else None,
        "cell_span_valid": span_valid,
        "cell_spans_overlap": spans_overlap,
        "sorted_by_8_byte_prefix": prefixes_sorted,
    }


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

    chunk_count = int.from_bytes(data[18:22], "big")
    expected_size = chunk_count * CHUNK_SIZE
    page_count = len(data) // PAGE_SIZE
    decoded_size_field = int.from_bytes(data[26:28], "big") ^ HEADER_SIZE_XOR
    decoded_header_bytes = [
        data[index] ^ xor for index, xor in zip((8, 9, 10), HEADER_BYTE_XORS)
    ]
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

    page_type_histogram = collections.Counter()
    type_05_pages = []
    for page_number in range(page_count):
        page = data[page_number * PAGE_SIZE : (page_number + 1) * PAGE_SIZE]
        nonzero_pages += any(page)
        page_type_histogram[page[0]] += 1
        if page_number % 2 == 0 and page[0] == DIRECTORY_TYPE:
            type_05_pages.append(directory_summary(data, page_number))

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
        "header_chunk_count_offset": 18,
        "header_chunk_count_width": 32,
        "decoded_header_bytes_8_10": decoded_header_bytes,
        "decoded_size_field": decoded_size_field,
        "decoded_size_field_matches_chunk_size": decoded_size_field == CHUNK_SIZE,
        "expected_size_from_header": expected_size,
        "size_matches_header": expected_size == len(data),
        "page_count": page_count,
        "page_pair_count": page_count // 2,
        "page_type_histogram": {
            f"0x{page_type:02x}": count
            for page_type, count in sorted(page_type_histogram.items())
        },
        "type_05_directory_pages": type_05_pages,
        "type_05_record_count_histogram": {
            str(record_count): sum(
                page["record_count"] == record_count for page in type_05_pages
            )
            for record_count in sorted(
                {page["record_count"] for page in type_05_pages}
            )
        },
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
