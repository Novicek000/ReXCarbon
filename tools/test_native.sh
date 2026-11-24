#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
NDK_DIR="${REX_NDK_DIR:-$TOOLS_DIR/android-ndk-r29}"
CLANG_BIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++"
"$CLANG_BIN" --target=aarch64-linux-android28 --sysroot="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
    -std=c++20 -static -O1 -Wall -Wextra -Werror -pthread \
    -I "$PROJECT_DIR/app/src/main/cpp" "$PROJECT_DIR/tests/native_test.cpp" \
    "$PROJECT_DIR/app/src/main/cpp/xdvdfs.cpp" -o "$TOOLS_DIR/native-test"
"$TOOLS_DIR/native-test" "$@"
