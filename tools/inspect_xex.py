#!/usr/bin/env python3
"""Inspect/extract only default.xex from a local XDVDFS image (no upload)."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

def inspect(iso, output=None):
    with open(iso, "rb") as stream:
        stream.seek(0, 2)
        size = stream.tell()
        def read(offset, count):
            if offset < 0 or count < 0 or offset + count > size:
                raise ValueError("ISO range out of bounds")
            stream.seek(offset)
            data = stream.read(count)
            if len(data) != count:
                raise ValueError("Truncated ISO")
            return data
        for base in (0, 0xfb20, 0x20600, 0x2080000, 0xfd90000):
            if base+33*2048 > size:
                continue
            header = read(base+32*2048, 2048)
            if header[:20] == header[-20:] == b"MICROSOFT*XBOX*MEDIA":
                break
        else:
            raise ValueError("XDVDFS not found")
        sector, count = struct.unpack_from("<II", header, 20)
        if not 14 <= count <= 32*1024*1024:
            raise ValueError("Invalid root directory length")
        table = read(base+sector*2048, count)
        pending, visited = [0], set()
        while pending:
            pos = pending.pop()
            if pos in visited or pos+14 > len(table):
                raise ValueError("Invalid directory node")
            visited.add(pos)
            left,right,start,length,attrs,nlen = struct.unpack_from("<HHIIBB", table, pos)
            if not nlen or pos+14+nlen > len(table):
                raise ValueError("Invalid filename")
            name = table[pos+14:pos+14+nlen]
            if name.lower() == b"default.xex" and not attrs & 0x10:
                data = read(base+start*2048, length)
                break
            pending.extend(node*4 for node in (left,right) if node)
        else:
            raise ValueError("default.xex not found")
    if data[:4] != b"XEX2" or len(data) < 24:
        raise ValueError("Invalid XEX2")
    result = {"iso": str(iso), "partition_offset": hex(base), "xex_size": len(data),
              "xex_sha256": hashlib.sha256(data).hexdigest(), "xex_sha1": hashlib.sha1(data).hexdigest()}
    number = struct.unpack_from(">I", data, 20)[0]
    if number > 4096 or 24+number*8 > len(data):
        raise ValueError("Invalid XEX optional headers")
    for i in range(number):
        key, value = struct.unpack_from(">II", data, 24+i*8)
        if key == 0x00010100:
            result["entry_point"] = f"0x{value:08X}"
        elif key == 0x00010201:
            result["image_base"] = f"0x{value:08X}"
        elif key == 0x00040006:
            if value+24 > len(data):
                raise ValueError("Invalid execution info")
            media, version, base_version, title = struct.unpack_from(">IIII", data, value)
            result.update(media_id=f"{media:08X}", title_id=f"{title:08X}", version=f"{version:08X}")
    if output:
        Path(output).parent.mkdir(parents=True, exist_ok=True)
        Path(output).write_bytes(data)
    return result

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("iso")
    parser.add_argument("--output")
    args = parser.parse_args()
    print(json.dumps(inspect(args.iso,args.output),indent=2))
