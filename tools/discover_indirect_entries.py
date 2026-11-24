#!/usr/bin/env python3
"""Find absent code targets in contiguous .rdata/.data pointer tables."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct

ROOT = Path(__file__).resolve().parents[1]
PORT = ROOT / "ports/game_europe"
image = (PORT / "image.bin").read_bytes()
known = {int(x, 16) for x in re.findall(r"\{ 0x([0-9A-Fa-f]+),", (PORT / "generated/carbon_europe_init.cpp").read_text())}
known.add(0x824DAC58)  # Already compiled and verified by 0.1.2.
pe = struct.unpack_from("<I", image, 0x3C)[0]
assert image[pe:pe+4] == b"PE\0\0"
count = struct.unpack_from("<H", image, pe+6)[0]
optional_size = struct.unpack_from("<H", image, pe+20)[0]
sections = []
for index in range(count):
    pos = pe+24+optional_size+40*index
    name = image[pos:pos+8].split(b"\0")[0].decode()
    size, rva = struct.unpack_from("<II", image, pos+8)
    flags = struct.unpack_from("<I", image, pos+36)[0]
    assert rva+size <= len(image)
    sections.append((name, rva, size, flags))
executable = [(0x82000000+rva, 0x82000000+rva+size) for _, rva, size, flags in sections if flags & 0x20000000]
references = {}
for name, rva, size, _ in sections:
    if name not in (".rdata", ".data"):
        continue
    run = []
    def flush():
        if len(run) >= 2:
            for pos, target in run:
                if target not in known:
                    references.setdefault(target, []).append({"section": name, "pointer_at": f"0x{0x82000000+pos:08X}"})
        run.clear()
    for pos in range(rva, rva+size-3, 4):
        target = struct.unpack_from(">I", image, pos)[0]
        if target % 4 == 0 and any(begin <= target < end for begin, end in executable):
            run.append((pos, target))
        else:
            flush()
    flush()
assert 0x82322188 in references or 0x82322188 in known, "The reported crash target must be found independently"
report = {
    "xex_sha256": hashlib.sha256((PORT / "default.xex").read_bytes()).hexdigest(),
    "rule": "aligned code addresses in contiguous runs of at least two pointers in .rdata/.data; excludes current mappings and 0x824DAC58",
    "entries": [{"address": f"0x{address:08X}", "references": refs} for address, refs in sorted(references.items())],
}
(PORT / "indirect_entries.json").write_text(json.dumps(report, indent=2)+"\n")
build = ROOT / "build/indirect-codegen"
build.mkdir(exist_ok=True)
shutil.copyfile(PORT / "default.xex", build / "default.xex")
manifest = (PORT / "carbon_europe.toml").read_text()
present = {int(x, 16) for x in re.findall(r'"0x([0-9A-Fa-f]+)"\s*=', manifest)}
manifest += "\n# Targets recovered from static pointer tables in this exact XEX.\n"
manifest += "".join(f'"0x{address:08X}" = {{}}\n' for address in sorted(references) if address not in present)
(build / "carbon_europe.toml").write_text(manifest)
print(f"Found {len(references)} missing indirect entries; manifest: {build / 'carbon_europe.toml'}")
