#!/bin/bash
# First run tools/prepare_input_system_test.py using host Python.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
NDK_DIR="$TOOLS_DIR/android-ndk-r29"
SDK_DIR="$PROJECT_DIR/ports/nfsmw-android/sdk"
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
    --target=aarch64-linux-android28 \
    --sysroot="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
    -std=c++23 -static -O2 -pthread -fno-char8_t -DSPDLOG_FMT_EXTERNAL=1 \
    -I "$PROJECT_DIR/integration" -I "$PROJECT_DIR/app/src/main/cpp" \
    -I "$SDK_DIR/include" -I "$TOOLS_DIR/rex-sdk/linux-arm64/include" \
    "$PROJECT_DIR/tests/rex_input_test.cpp" "$PROJECT_DIR/build/input-test-core.cpp" \
    "$SDK_DIR/src/input/device_assignment.cpp" "$SDK_DIR/src/input/state_merge.cpp" \
    -o "$TOOLS_DIR/rex-input-test"
"$TOOLS_DIR/rex-input-test"
