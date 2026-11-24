Upstream API reference checked on 2026-11-03:

* ReXGlue: `c94f5ebdcb3c9d1a460ca48e04f9758448f8d518` (main, SDK version 0.10.0).
  https://github.com/rexglue/rexglue-sdk/tree/c94f5ebdcb3c9d1a460ca48e04f9758448f8d518
  Official ARM64 CLI used to generate the local Carbon Europe guest sources.
  The Android runtime comes from the matching third-party port below.
* libadrenotools: `8fae8ce254dfc1344527e05301e43f37dea2df80`.
  https://github.com/bylaws/libadrenotools/tree/8fae8ce254dfc1344527e05301e43f37dea2df80
* liblinkernsbypass: `aa3975893d83ef1bc84c321ec60c65fbf1287887`.
  https://github.com/bylaws/liblinkernsbypass/tree/aa3975893d83ef1bc84c321ec60c65fbf1287887

Dependency licenses remain in `third_party/`. These sources are vendored for
reproducible offline compilation of the frontend. `tools/fetch_deps.py` restores
the same revisions without depending on `git submodule` support.

Carbon Europe integration:

* Android runtime libraries from codepdbh/NFS-CARBON-360-ANDROID-RECOMP v0.3.3.
  The official release APK SHA-256 was verified against its SHA256SUMS file:
  `4380acf277e52e74cd25bdb3c140d29af58f3bdc60a9b8273636f1ed4ba98fd7`.
  The old `libmain.so` containing the other edition's game code is NOT used.
* Matching Android SDK headers and ReXApp source:
  codepdbh/nfsmw-android `a7e6e4c3ca71064d0eea56d91fcc8aaf63418fc7`.
  Local modification: use a 64-bit operand when writing FPCR in fpscr.h.
* SDL Java Android glue from libsdl-org/SDL
  `8bf3b7215ad9fc3deb583c6a3a37c6c67f2e24e4`.
* Analysis helpers/XEX WebAssembly decoder source from
  codepdbh/NFS-CARBON-360-ANDROID-RECOMP
  `d56bf0238a28372ee544422ce951faeaaeb1884b`.

Guest source code is generated locally from the supplied XEX. Its executable
identity, build instructions and provenance are recorded in
`ports/game_europe/README.md` and `runtime-provenance.json`.

The Android ARM64 build on this device uses the unofficial host tool repacks:

* https://github.com/lzhiyong/termux-ndk (r29, host executables for ARM64).
* https://github.com/lzhiyong/android-sdk-tools (35.0.2, native aapt2/zipalign).
* Google Android API 35 android.jar and build-tools 35.0.0 D8/APK signer.
* Azul Zulu JDK 17 and Kitware CMake 3.31.6 for Linux AArch64.

On a standard x86_64 development machine use Google's official SDK/NDK and
Gradle instead; the project has no Java library dependencies.
