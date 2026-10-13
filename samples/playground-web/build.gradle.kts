import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    compilerOptions {
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "playground.js"
                mode = KotlinWebpackConfig.Mode.PRODUCTION
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("wasmJsMain").dependencies {
            implementation(project(":samples:playground"))
        }
    }
}
