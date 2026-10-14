import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

group = "br.com.faru.boitata"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    compilerOptions {
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
        optIn.add("br.com.faru.boitata.core.AdvancedBoitataApi")
    }
    jvm()

    sourceSets {
        getByName("jvmMain").dependencies {
            api(project(":boitata-core"))
            api(compose.desktop.currentOs)
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(project(":boitata-light"))
            implementation(project(":boitata-weather"))
            implementation(project(":boitata-scene"))
            implementation(project(":boitata-core"))
        }
    }
}
