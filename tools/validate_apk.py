#!/usr/bin/env python3
"""Check the packaged ELF ABI, dependencies, guest mappings and current module."""
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/rex-launcher-debug.apk"


def elf(data):
    h = struct.unpack_from("<16sHHIQQQIHHHHHH", data)
    assert h[0][:6] == b"\x7fELF\x02\x01" and h[2] == 183, "ELF64 AArch64 required"
    ph = [struct.unpack_from("<IIQQQQQQ", data, h[5] + i*h[9]) for i in range(h[10])]
    loads = [p for p in ph if p[0] == 1]
    assert loads and all(p[7] >= 16384 and p[2] % 16384 == p[3] % 16384 for p in loads)
    dynamic = next(p for p in ph if p[0] == 2)
    tags = []
    for pos in range(dynamic[2], dynamic[2]+dynamic[5], 16):
        tag, value = struct.unpack_from("<qQ", data, pos)
        if tag == 0:
            break
        tags.append((tag, value))
    address = next(value for tag, value in tags if tag == 5)
    p = next(p for p in loads if p[3] <= address < p[3]+p[5])
    strings = p[2] + address - p[3]
    needed = []
    for tag, value in tags:
        if tag == 1:
            start = strings+value
            needed.append(data[start:data.index(b"\0", start)].decode())
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, h[6]+i*h[11]) for i in range(h[12])]
    symbols = set()
    for section in sections:
        if section[1] != 11:
            continue
        table = sections[section[6]]
        for pos in range(section[4], section[4]+section[5], section[9]):
            name, _, _, index, _, _ = struct.unpack_from("<IBBHQQ", data, pos)
            if name and index:
                start = table[4]+name
                symbols.add(data[start:data.index(b"\0", start)].decode())
    return {"bytes": len(data), "min_load_alignment": min(p[7] for p in loads), "needed": needed}, symbols


with zipfile.ZipFile(APK) as archive:
    assert archive.testzip() is None, "ZIP CRC"
    assert len(archive.namelist()) == len(set(archive.namelist())), "duplicate ZIP entries"
    natives, symbols, module = [], set(), None
    for name in archive.namelist():
        if name.endswith(".so"):
            assert name.startswith("lib/arm64-v8a/")
            data = archive.read(name)
            item, exports = elf(data)
            natives.append({"name": name, **item})
            symbols.update(exports)
            if name.endswith("/libcarbon_europe.so"):
                module = data
                assert "Java_pl_rexlauncher_CarbonNative_setLaunchReport" in exports
                assert "SDL_main" in exports
                assert "sub_824DAC58" in exports and "__imp__sub_824DAC58" in exports
                assert "sub_825BF858" in exports and "__imp__sub_825BF858" in exports
                assert b"SAF ISO mounted with pread" in data
                assert b"Verified supplemental PPC entry 0x824DAC58" in data
                assert b"supplemental static indirect entries" in data
                assert b"Android XInput user 0: status" in data
                assert b"Verified career PPC entry 0x825BF858 -> tail 0x825BF1F8" in data
                assert b"Unresolved branch from" not in data
    assert len(natives) == 11 and module is not None
    assert module == (ROOT / "build/carbon-module/libcarbon_europe.so").read_bytes()
    metadata = json.loads(archive.read("assets/carbon-module.json"))
    icons = [name for name in archive.namelist() if name.endswith('/carbon_icon.png')]
    assert len(icons) == 1
    icon_digest = hashlib.sha256(archive.read(icons[0])).hexdigest()
    icon_provenance = json.loads((ROOT/'ports/game_europe/icon-provenance.json').read_text())
    assert icon_digest == icon_provenance['png_sha256'] == metadata['iso_icon_sha256']
    assert 'resources.arsc' in archive.namelist()
    assert any('ic_launcher.xml' in name for name in archive.namelist())
    spatial = json.loads(archive.read('assets/graphics/provenance.json'))
    assert hashlib.sha256(archive.read('assets/graphics/provenance.json')).hexdigest() == metadata['image_enhancements']['shader_provenance_sha256']
    for name,digest in spatial['shaders'].items():
        assert hashlib.sha256(archive.read('assets/graphics/'+name)).hexdigest() == digest,name
    assert len(spatial['shaders']) == 5
    assert b'FsrEasuF(rgb' in archive.read('assets/graphics/fsr_easu.frag')
    assert b'FsrRcasF(rgb' in archive.read('assets/graphics/fsr_rcas.frag')
    assert b'CasFilter(rgb' in archive.read('assets/graphics/cas.frag')
    assert metadata['image_enhancements']['default_filter'] == 'off'
    assert metadata['image_enhancements']['frame_generation'] is False
    for native_flag in (b'swap_post_effect',b'anisotropic_override',b'resolution_scale'):
        assert any(native_flag in archive.read(name) for name in archive.namelist() if name.endswith('.so')),native_flag
assert metadata['application_name'] == 'ReX Carbon'
assert metadata['cpu_optimization'].startswith('-O2;')
assert metadata['output_resolution_modes'][-2:] == ['native-screen', 'custom']
indirect_manifest_path = ROOT/'ports/game_europe/supplemental_indirect.json'
indirect_manifest = json.loads(indirect_manifest_path.read_text())
assert metadata['supplemental_indirect_manifest_sha256'] == hashlib.sha256(indirect_manifest_path.read_bytes()).hexdigest()
assert metadata['supplemental_indirect_entry_count'] == len(indirect_manifest['entries'])
for entry in indirect_manifest['entries']:
    assert entry['symbol'] in symbols and '__imp__'+entry['symbol'] in symbols, entry
assert 'sub_82322188' in symbols
career_manifest_path = ROOT/'ports/game_europe/supplemental_825BF858.json'
career_manifest = json.loads(career_manifest_path.read_text())
assert metadata['career_entry_manifest_sha256'] == hashlib.sha256(career_manifest_path.read_bytes()).hexdigest()
assert career_manifest['entry'] == '0x825BF858' and career_manifest['tail_target'] == '0x825BF1F8'
assert metadata['supplemental_guest_entries'] == ['0x824DAC58', '0x825BF858']
career_source = (ROOT/'ports/game_europe/supplemental_825BF858.cpp').read_text()
career_body = re.search(r'^DEFINE_REX_FUNC\(sub_825BF858\) \{.*?^\}', career_source, re.M | re.S).group(0)
assert hashlib.sha256(career_body.encode()).hexdigest() == career_manifest['generated_body_sha256']

public = {"libandroid.so", "libc.so", "libdl.so", "liblog.so", "libm.so", "libOpenSLES.so"}
packaged = {Path(item["name"]).name for item in natives}
for item in natives:
    assert set(item["needed"]) <= packaged | public, item
    assert "libvulkan.so" not in item["needed"]
generated = (ROOT / "ports/game_europe/generated/carbon_europe_init.cpp").read_text()
mappings = re.findall(r"\{ 0x[0-9A-Fa-f]+, (\w+) \}", generated)
assert len(mappings) == 72429
assert not set(mappings)-symbols, sorted(set(mappings)-symbols)[:10]
result = {
    "apk": APK.name, "bytes": APK.stat().st_size,
    "sha256": hashlib.sha256(APK.read_bytes()).hexdigest(),
    "native": natives, "guest_function_mappings": len(mappings),
    "all_mapped_symbols_present": True, "metadata": metadata,
    "crc_check": "passed", "elf_alignment_check": "passed", "dependency_check": "passed",
    "saf_vfs": "direct duplicated FD; pread; per-file/range mappings; no whole-ISO mapping",
    "supplemental_guest_entries": metadata['supplemental_guest_entries'],
    "supplemental_indirect_entry_count": len(indirect_manifest['entries']),
    "guest_refresh_rates_hz": [30, 60],
    "orientations": metadata['orientation_modes'],
    "output_resolution_modes": metadata['output_resolution_modes'],
    "application_name": metadata['application_name'],
    "launcher_locales": metadata['launcher_locales'],
    "iso_icon_sha256": icon_digest,
    "cpu_optimization": metadata['cpu_optimization'],
    "image_enhancements": metadata['image_enhancements'],
    "android_input_assignment": metadata['android_input_assignment'],
    "gameplay_verified": False,
}
(ROOT / "build/validation.json").write_text(json.dumps(result, indent=2)+"\n")
print(json.dumps({key: value for key, value in result.items() if key not in ("native", "metadata")}, indent=2))
