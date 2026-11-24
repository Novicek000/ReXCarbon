#!/usr/bin/env python3
"""Expand the pinned, MIT-licensed AMD spatial filters for OpenGL ES 3.1."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[1]
VENDOR=ROOT/'third_party/fidelityfx-spatial'
OUT=ROOT/'app/src/main/assets/graphics'
OUT.mkdir(parents=True,exist_ok=True)
provenance=json.loads((VENDOR/'provenance.json').read_text())
for name,item in provenance.items():
    assert hashlib.sha256((VENDOR/name).read_bytes()).hexdigest()==item['sha256'],name
shaders={}
for template in sorted((ROOT/'integration/spatial-shaders').iterdir()):
    if template.suffix not in ('.vert','.frag'):continue
    source=template.read_text()
    for name in ['ffx_a.h','ffx_fsr1.h','ffx_cas.h']:
        source=source.replace('#include "'+name+'"',(VENDOR/name).read_text())
    # ESSL requires unsigned zero literals when assigning uint/uvec elements.
    # These casts preserve AMD's bit-exact constants; original headers are untouched.
    source=source.replace('con3[2]=con3[3]=0;','con3[2]=con3[3]=0u;').replace('con[2]=0;','con[2]=0u;').replace('con[3]=0;','con[3]=0u;').replace('const1[3]=0;','const1[3]=0u;')
    (OUT/template.name).write_text(source)
    shaders[template.name]=hashlib.sha256(source.encode()).hexdigest()
licenses=ROOT/'app/src/main/assets/licenses/fidelityfx';licenses.mkdir(exist_ok=True)
for source in VENDOR.glob('*LICENSE.txt'):shutil.copyfile(source,licenses/source.name)
(OUT/'provenance.json').write_text(json.dumps({'upstream':provenance,'shaders':shaders,'essl_changes':'uint zero literal suffixes only; full-precision FP32 AMD paths'},indent=2)+'\n')
print(f'Generated {len(shaders)} spatial shader stages; all vendor hashes verified')
