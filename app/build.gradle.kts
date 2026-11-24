plugins { id("com.android.application") }

android {
    namespace = "pl.rexlauncher"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.rexlauncher"
        minSdk = 28
        targetSdk = 35
        versionCode = 9
        versionName = "0.4.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            arguments += "-DANDROID_STL=c++_static"
            project.providers.gradleProperty("rexGameSource").orNull?.let {
                arguments += "-DREX_GAME_SOURCE=$it"
            }
        } }
    }
    ndkVersion = "29.0.14206865"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
