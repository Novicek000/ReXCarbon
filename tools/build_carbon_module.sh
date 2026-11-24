#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
NDK_DIR="$TOOLS_DIR/android-ndk-r29"
CMAKE_BIN="$TOOLS_DIR/cmake-3.31.6-linux-aarch64/bin/cmake"
BUILD_DIR="$TOOLS_DIR/carbon-europe-build"
"$CMAKE_BIN" -S "$PROJECT_DIR/ports/game_europe" -B "$BUILD_DIR" -G 'Unix Makefiles' \
    -DCMAKE_MAKE_PROGRAM="$NDK_DIR/prebuilt/linux-x86_64/bin/make" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release '-DCMAKE_CXX_FLAGS_RELEASE=-O2 -DNDEBUG'
# ~3 GiB available RAM on this phone. A single compiler avoids OOM kills.
"$CMAKE_BIN" --build "$BUILD_DIR" -j 1
mkdir -p "$PROJECT_DIR/build/carbon-module"
cp "$BUILD_DIR/libcarbon_europe.so" "$PROJECT_DIR/build/carbon-module/"
cp "$TOOLS_DIR/carbon-runtime/"*.so "$PROJECT_DIR/build/carbon-module/"
# Match the headers used by this compiler while preserving the older ABI.
cp "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" \
    "$PROJECT_DIR/build/carbon-module/"
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$PROJECT_DIR/build/carbon-module/libcarbon_europe.so"
