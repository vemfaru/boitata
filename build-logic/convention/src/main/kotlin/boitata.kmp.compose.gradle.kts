/**
 * Boitatá modules that draw: all targets plus Compose.
 * A module may depend on other Boitatá modules and on Compose only: no game names, colours, art, resources or rules.
 */
plugins {
    id("boitata.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            implementation(catalogLibs.findLibrary("kotlinx-coroutines-core").get())
            // Pins the lifecycle version Compose pulls in transitively, so every module resolves the same one.
            implementation(catalogLibs.findLibrary("androidx-lifecycle-runtime-compose").get())
        }
    }
}
