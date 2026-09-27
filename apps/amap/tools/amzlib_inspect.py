#!/usr/bin/env python3
import argparse
import hashlib
import json
import struct
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path


MAGIC = b"AM-zlib\0"


@dataclass
class RecordSummary:
    ordinal: int
    block_id: int
    offset: int
    compressed_size: int
    uncompressed_size: int
    uncompressed_sha256: str


def parse_container(path: Path):
    data = path.read_bytes()
    if data[:8] != MAGIC:
        raise ValueError(f"unexpected magic: {data[:8]!r}")
    if len(data) < 180:
        raise ValueError("file is shorter than the verified header")

    first_record_offset = struct.unpack(">I", data[112:116])[0]
    records_end = struct.unpack(">I", data[120:124])[0]
    header_record_count = struct.unpack(">I", data[28:32])[0]
    uncompressed_chunk_size = struct.unpack(">I", data[172:176])[0]
    if first_record_offset < 180 or records_end > len(data) or first_record_offset > records_end:
        raise ValueError("invalid record bounds")

    records = []
    offset = first_record_offset
    while offset < records_end:
        if offset + 6 > records_end:
            raise ValueError(f"truncated record header at {offset}")
        block_id, compressed_size = struct.unpack(">IH", data[offset : offset + 6])
        payload_start = offset + 6
        payload_end = payload_start + compressed_size
        if payload_end > records_end:
            raise ValueError(f"truncated record payload at {offset}")
        uncompressed = zlib.decompress(data[payload_start:payload_end])
        records.append(
            RecordSummary(
                ordinal=len(records),
                block_id=block_id,
                offset=offset,
                compressed_size=compressed_size,
                uncompressed_size=len(uncompressed),
                uncompressed_sha256=hashlib.sha256(uncompressed).hexdigest(),
            )
        )
        offset = payload_end

    if offset != records_end:
        raise ValueError(f"record parser stopped at {offset}, expected {records_end}")
    if header_record_count != len(records):
        raise ValueError(f"header count {header_record_count} != parsed count {len(records)}")

    block_ids = [record.block_id for record in records]
    unique_block_ids = set(block_ids)
    expected_block_ids = set(range(2, 2 * len(records) + 2, 2))
    summary = {
        "path": str(path),
        "file_size": len(data),
        "file_sha256": hashlib.sha256(data).hexdigest(),
        "magic": MAGIC.decode("ascii").rstrip("\0"),
        "first_record_offset": first_record_offset,
        "records_end": records_end,
        "trailing_zero_bytes": len(data) - records_end
        if data[records_end:] == b"\0" * (len(data) - records_end)
        else None,
        "header_record_count": header_record_count,
        "parsed_record_count": len(records),
        "uncompressed_chunk_size": uncompressed_chunk_size,
        "unique_block_ids": len(unique_block_ids),
        "min_block_id": min(block_ids),
        "max_block_id": max(block_ids),
        "even_complete_block_id_sequence": unique_block_ids == expected_block_ids,
        "record_order_equals_block_id_order": block_ids == sorted(block_ids),
        "compressed_bytes": sum(record.compressed_size for record in records),
        "uncompressed_bytes": sum(record.uncompressed_size for record in records),
        "records": [asdict(record) for record in records],
    }
    return data, records, summary


def reconstruct(path: Path, output: Path):
    data, records, summary = parse_container(path)
    if not summary["even_complete_block_id_sequence"]:
        raise ValueError("cannot map records to DICE page pairs: block IDs are incomplete")
    if summary["uncompressed_chunk_size"] != 8192:
        raise ValueError("reconstruction currently expects 8192-byte chunks")

    chunks = {}
    offset = summary["first_record_offset"]
    for record in records:
        compressed_size = record.compressed_size
        payload_start = offset + 6
        payload_end = payload_start + compressed_size
        chunks[record.block_id] = zlib.decompress(data[payload_start:payload_end])
        offset = payload_end

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as stream:
        for block_id in sorted(chunks):
            stream.write(chunks[block_id])
    return summary


def main():
    parser = argparse.ArgumentParser(
        description="Inspect AMap AM-zlib containers without printing decompressed content."
    )
    parser.add_argument("input", type=Path)
    parser.add_argument(
        "--reconstruct",
        type=Path,
        help="write block-ID-sorted 4096-byte DICE pages to this path",
    )
    parser.add_argument("--full-records", action="store_true")
    args = parser.parse_args()

    if args.reconstruct:
        summary = reconstruct(args.input, args.reconstruct)
        summary["reconstructed_path"] = str(args.reconstruct)
    else:
        _, _, summary = parse_container(args.input)
    if not args.full_records:
        summary.pop("records", None)
    print(json.dumps(summary, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
