#!/bin/bash
# Run inside the enabled Linux ARM64 environment:
# mc-linux -- /bin/bash /workspace/rex-launcher/tools/build_arm64.sh
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
JDK_DIR="${REX_JDK_DIR:-$TOOLS_DIR/zulu17.70.113-ca-jdk17.0.21.0.3-linux_aarch64}"
NDK_DIR="${REX_NDK_DIR:-$TOOLS_DIR/android-ndk-r29}"
CMAKE_BIN="${REX_CMAKE_BIN:-$TOOLS_DIR/cmake-3.31.6-linux-aarch64/bin/cmake}"
MAKE_BIN="$NDK_DIR/prebuilt/linux-x86_64/bin/make"
AAPT_BIN="$TOOLS_DIR/android-tools/build-tools/aapt2"
ZIPALIGN_BIN="$TOOLS_DIR/android-tools/build-tools/zipalign"
ANDROID_JAR="$TOOLS_DIR/platform/android-35/android.jar"
BUILD_TOOLS="$TOOLS_DIR/build-tools/android-15"
BUILD_DIR="${REX_BUILD_DIR:-$TOOLS_DIR/launcher-build}"
OUTPUT_DIR="$PROJECT_DIR/build"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$BUILD_DIR/generated" "$OUTPUT_DIR"
"$CMAKE_BIN" -S "$PROJECT_DIR/app/src/main/cpp" -B "$BUILD_DIR/native" \
    -G 'Unix Makefiles' -DCMAKE_MAKE_PROGRAM="$MAKE_BIN" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_DIR/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
    -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
    -DREX_GAME_SOURCE="${REX_GAME_SOURCE:-}"
"$CMAKE_BIN" --build "$BUILD_DIR/native" -j 3
"$AAPT_BIN" compile --no-crunch --dir "$PROJECT_DIR/app/src/main/res" -o "$BUILD_DIR/compiled-res.zip"
sed 's/<manifest /<manifest package="pl.rexlauncher" /' "$PROJECT_DIR/app/src/main/AndroidManifest.xml" > "$BUILD_DIR/AndroidManifest.xml"
"$AAPT_BIN" link -I "$ANDROID_JAR" --manifest "$BUILD_DIR/AndroidManifest.xml" \
    --min-sdk-version 28 --target-sdk-version 35 --version-code 9 --version-name 0.4.0 \
    --java "$BUILD_DIR/generated" --custom-package pl.rexlauncher \
    -o "$BUILD_DIR/base.apk" "$BUILD_DIR/compiled-res.zip"
find "$PROJECT_DIR/app/src/main/java" "$BUILD_DIR/generated" -name '*.java' | LC_ALL=C sort > "$BUILD_DIR/sources.txt"
"$JDK_DIR/bin/javac" --release 17 -encoding UTF-8 -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
"$JDK_DIR/bin/jar" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"$JDK_DIR/bin/java" -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 28 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
mkdir -p "$BUILD_DIR/package/lib/arm64-v8a"
cp "$BUILD_DIR/dex/classes.dex" "$BUILD_DIR/package/"
cp -R "$PROJECT_DIR/app/src/main/assets" "$BUILD_DIR/package/"
for lib in rexlauncher adrenotools/src/hook/hook_impl adrenotools/src/hook/main_hook \
           adrenotools/src/hook/file_redirect_hook adrenotools/src/hook/gsl_alloc_hook; do
    lib_path="$BUILD_DIR/native/$(dirname "$lib")/lib$(basename "$lib").so"
    cp "$lib_path" "$BUILD_DIR/package/lib/arm64-v8a/"
done
"$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$BUILD_DIR/package/lib/arm64-v8a/"*.so
if [ -f "$PROJECT_DIR/build/carbon-module/libcarbon_europe.so" ]; then
    for lib in carbon_europe SDL3 c++_shared rexruntime rexgpu-xenos rex_android_gpu_driver \
               hook_impl main_hook file_redirect_hook gsl_alloc_hook; do
        cp "$PROJECT_DIR/build/carbon-module/lib$lib.so" "$BUILD_DIR/package/lib/arm64-v8a/"
    done
fi
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
( cd "$BUILD_DIR/package"; "$JDK_DIR/bin/jar" uf "$BUILD_DIR/unsigned.apk" classes.dex lib assets )
"$ZIPALIGN_BIN" -f -P 16 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
if [ ! -f "$BUILD_DIR/debug.jks" ]; then
    "$JDK_DIR/bin/keytool" -genkeypair -keystore "$BUILD_DIR/debug.jks" -storepass android \
        -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 \
        -dname 'CN=ReX Launcher Development'
fi
"$JDK_DIR/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign --ks "$BUILD_DIR/debug.jks" \
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --out "$OUTPUT_DIR/rex-launcher-debug.apk" "$BUILD_DIR/aligned.apk"
"$JDK_DIR/bin/java" -jar "$BUILD_TOOLS/lib/apksigner.jar" verify --verbose "$OUTPUT_DIR/rex-launcher-debug.apk"
"$ZIPALIGN_BIN" -c -P 16 4 "$OUTPUT_DIR/rex-launcher-debug.apk"
( cd "$OUTPUT_DIR"; sha256sum rex-launcher-debug.apk > rex-launcher-debug.apk.sha256 )
printf 'APK: %s\n' "$OUTPUT_DIR/rex-launcher-debug.apk"
