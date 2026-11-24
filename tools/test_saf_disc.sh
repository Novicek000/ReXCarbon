#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
NDK_DIR="$TOOLS_DIR/android-ndk-r29"
SDK_DIR="$PROJECT_DIR/ports/nfsmw-android/sdk"
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" \
    --target=aarch64-linux-android28 \
    --sysroot="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
    -std=c++23 -static -O1 -pthread -fno-char8_t -DSPDLOG_FMT_EXTERNAL=1 \
    -ffunction-sections -fdata-sections -Wl,--gc-sections \
    -I "$PROJECT_DIR/app/src/main/cpp" -I "$PROJECT_DIR/integration" \
    -I "$SDK_DIR/include" -I "$TOOLS_DIR/rex-sdk/linux-arm64/include" \
    -I "$TOOLS_DIR/rex-sdk/linux-arm64/include/utf8cpp" \
    "$PROJECT_DIR/tests/saf_disc_test.cpp" "$PROJECT_DIR/integration/rex_saf_disc.cpp" \
    "$PROJECT_DIR/app/src/main/cpp/xdvdfs.cpp" "$SDK_DIR/src/filesystem/device.cpp" \
    "$SDK_DIR/src/filesystem/entry.cpp" "$SDK_DIR/src/core/utf8.cpp" \
    "$SDK_DIR/src/core/mutex.cpp" "$SDK_DIR/src/core/string_buffer.cpp" \
    -o "$TOOLS_DIR/saf-disc-test"
"$TOOLS_DIR/saf-disc-test" "$@"
