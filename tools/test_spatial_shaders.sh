#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
VALIDATOR="${GLSLANG_VALIDATOR:-$TOOLS_DIR/shader-tools/usr/bin/glslangValidator}"
export LD_LIBRARY_PATH="$TOOLS_DIR/shader-tools/usr/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
cd "$PROJECT_DIR/app/src/main/assets/graphics"
for shader_file in fullscreen.vert external.frag fsr_easu.frag fsr_rcas.frag cas.frag; do
    "$VALIDATOR" "$shader_file"
done
for fragment in external.frag fsr_easu.frag fsr_rcas.frag cas.frag; do
    "$VALIDATOR" -l fullscreen.vert "$fragment"
done
printf 'PASS: 5 ESSL 3.10 shader stages and 4 linked programs\n'
