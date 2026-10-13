import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * Every client target: Android (through the library plugin), iOS, desktop (JVM) and web (wasmJs). Anything in commonMain must
 * have a wasm story: no platform-only libraries without an expect/actual.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(catalogLibs.findVersion("jvm-toolchain").get().requiredVersion.toInt())

    iosArm64()
    iosSimulatorArm64()
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Test JVMs default to 512 MB, which small Compose and Skia scenes can outgrow on a CI runner.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "1g"
}
