#!/usr/bin/env python3
"""Restore only PPC jumps whose destination label exists in the SAME function.

Based on codepdbh/NFS-CARBON-360-ANDROID-RECOMP/tools/fix_local_branches.py.
Missing targets stay fatal; this does not replace missing code with no-ops.
"""
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1] / "ports/game_europe"
FUNCTION = re.compile(r"(?m)^DEFINE_REX_FUNC\(")
BRANCH = re.compile(r'(?m)^(\tif \([^\n]+?\)) REX_FATAL\("Unresolved branch from 0x([0-9A-F]{8}) to 0x([0-9A-F]{8})"\);$')
SWITCH = re.compile(r'(?m)^(\t+)REX_FATAL\("Jump target 0x([0-9A-F]{8}) unresolved at bctr 0x[0-9A-F]{8}"\);$')

def main():
    counts = {"fixed": 0, "verified_return_tails": 0, "unresolved": 0, "changed_files": 0, "files": 0}
    image_path = ROOT / "image.bin"
    image = image_path.read_bytes() if image_path.exists() else b""
    for path in sorted((ROOT / "generated").glob("carbon_europe_recomp.*.cpp")):
        counts["files"] += 1
        original = path.read_text()
        starts = [match.start() for match in FUNCTION.finditer(original)] + [len(original)]
        output = [original[:starts[0]]] if len(starts) > 1 else [original]
        for first, last in zip(starts, starts[1:]):
            body = original[first:last]
            labels = set(re.findall(r"(?m)^loc_([0-9A-F]{8}):$", body))
            def branch(match):
                condition, source, target = match.groups()
                if target in labels:
                    counts["fixed"] += 1
                    return f"{condition} goto loc_{target};"
                # Some discovery fragments branch to a shared return block
                # before the fragment's entrypoint. Translate only after checking
                # the actual source branch and BOTH target instructions.
                src, dst = int(source, 16), int(target, 16)
                so, do = src-0x82000000, dst-0x82000000
                if 0 <= so <= len(image)-4 and 0 <= do <= len(image)-8:
                    word = struct.unpack_from(">I", image, so)[0]
                    displacement = word & 0xfffc
                    if displacement & 0x8000:
                        displacement -= 0x10000
                    tail = struct.unpack_from(">II", image, do)
                    if word >> 26 == 16 and word & 3 == 0 and src+displacement == dst and tail == (0x38600000,0x4e800020):
                        counts["verified_return_tails"] += 1
                        return (f"{condition} {{ ctx.r3.s64 = 0; return; }}"
                                f" // verified li r3,0; blr at 0x{target}")
                counts["unresolved"] += 1
                return match[0]
            def switch(match):
                indent, target = match.groups()
                if target in labels:
                    counts["fixed"] += 1
                    return f"{indent}goto loc_{target};"
                counts["unresolved"] += 1
                return match[0]
            output.append(SWITCH.sub(switch, BRANCH.sub(branch, body)))
        result = "".join(output)
        if result != original:
            path.write_text(result)
            counts["changed_files"] += 1
    if not counts["files"]:
        raise SystemExit("No generated sources; codegen has not succeeded")
    (ROOT / "branch-repair.json").write_text(json.dumps(counts, indent=2)+"\n")
    print(json.dumps(counts))

if __name__ == "__main__":
    main()
