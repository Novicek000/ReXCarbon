# ReX Carbon

ReX Carbon runs the Xbox 360 edition of **Need for Speed: Carbon** on ARM64 Android 9 and later. It uses a statically recompiled game module, ReXGlue and a Vulkan graphics backend. Supply your own disc image through the Android file picker.

## Compatible disc

Use a dump of **Need for Speed: Carbon (Europe) (En, Nl, Sv, Da, Fi)**. The launcher checks the executable inside the ISO. Renaming a different edition will not make it compatible.

| Identifier | Supported value |
| --- | --- |
| Title ID | `454107EC` |
| Media ID | `573FDB37` |
| Executable version | `0.0.0.4` |
| SHA-256 of default.xex inside the ISO | `f72ce8d9bab3284e4555a1675d19df13f9f665c0ade014a7666a924dce7e10a1` |

## Install and play

1. Download the arm64 APK from [Releases](https://github.com/Novicek000/ReXCarbon/releases).
2. Open the APK and confirm installation in Android. In Garage, choose your ISO and launch the game.
3. Open **Tools → App language** for System default, English or Polski. This setting changes the launcher interface.
4. In **Display**, select output resolution, frame rate, orientation and optional FSR 1, CAS or FXAA. Touch controls give way to a connected gamepad. Export diagnostic logs from **Tools**.

Starting with 0.4.0, the launcher checks the latest stable GitHub release when opened. It asks before downloading, checks the SHA-256, package ID, version and signing certificate, then opens the Android installer. Users on 0.3.0 need to install 0.4.0 manually once.

## Source and builds

Android UI, input, update checks and GPU filters live under `app/src/main/java`. JNI is in `app/src/main/cpp`. ReXGlue integration and shader templates are in `integration`. The project targets Android API 35 and uses JDK 17. Run `python3 tools/fetch_deps.py` to restore pinned Adreno Tools sources before building the frontend. The playable build also needs the Europe game module and ARM64 runtime libraries. See [module build notes](ports/game_europe/README.md) and [dependency revisions](DEPENDENCIES.md). Generate game sources locally from your own disc.

For maintainers, create stable releases tagged `vMAJOR.MINOR.PATCH` and attach `ReX-Carbon-MAJOR.MINOR.PATCH-arm64.apk`. Increment Android versionCode and sign with the existing app key so Android can update the installed package.
