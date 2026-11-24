#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
NDK_DIR="$TOOLS_DIR/android-ndk-r29"
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
    --target=aarch64-linux-android28 \
    --sysroot="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
    -std=c++23 -static -O2 -fno-char8_t -DSPDLOG_FMT_EXTERNAL=1 \
    -I "$PROJECT_DIR/ports/nfsmw-android/sdk/include" -I "$TOOLS_DIR/rex-sdk/linux-arm64/include" \
    -I "$PROJECT_DIR/ports/game_europe/generated" \
    "$PROJECT_DIR/tests/carbon_entry_test.cpp" \
    "$PROJECT_DIR/ports/game_europe/supplemental_824DAC58.cpp" \
    -o "$TOOLS_DIR/carbon-entry-test"
"$TOOLS_DIR/carbon-entry-test"
