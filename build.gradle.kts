/**
 * Declares every plugin the build uses, without applying them here. This pins one
 * classloader for the Kotlin Gradle plugin across the whole build — without it,
 * modules using convention plugins and modules using `alias(...)` each load their
 * own KGP and the build fails with "loaded multiple times in different subprojects".
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.kmp.library) apply false
}
