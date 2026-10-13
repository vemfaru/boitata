/**
 * A Boitatá library module: all targets plus the Android library variant.
 * Modules applying this MUST set their own `android.namespace`.
 */
plugins {
    id("boitata.kmp.targets")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    explicitApiWarning()
    androidLibrary {
        compileSdk = catalogLibs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = catalogLibs.findVersion("android-minSdk").get().requiredVersion.toInt()
    }
}
