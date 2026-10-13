import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
    jvm()

    sourceSets {
        getByName("jvmMain").dependencies {
            implementation(project(":samples:playground"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "br.com.faru.boitata.playground.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt()))
        }.get().metadata.installationPath.asFile.absolutePath
    }
}

// ./gradlew :samples:playground-desktop:renderClips [-Pclip=name] [-Ppreview]   (frames as PNGs; docs/media/render.sh turns them into GIFs)
tasks.register<JavaExec>("renderClips") {
    group = "boitata"
    description = "Draws every clip of the playground frame by frame into build/clips/<name>/NNNN.png"
    val jvm = kotlin.jvm().compilations.getByName("main")
    classpath(jvm.output.allOutputs, jvm.runtimeDependencyFiles)
    mainClass.set("br.com.faru.boitata.playground.RenderClipsKt")
    args(listOfNotNull(layout.buildDirectory.dir("clips").get().asFile.absolutePath, project.findProperty("clip")?.toString() ?: "", project.findProperty("preview")?.let { "preview" }))
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt())) })
}

// ./gradlew :samples:playground-desktop:benchFrames   (how long a weather frame takes to draw; CPU raster, so compare before and after, not against 16 ms)
tasks.register<JavaExec>("benchFrames") {
    group = "boitata"
    description = "Times the weather scene frame by frame"
    val jvm = kotlin.jvm().compilations.getByName("main")
    classpath(jvm.output.allOutputs, jvm.runtimeDependencyFiles)
    mainClass.set("br.com.faru.boitata.playground.BenchWeatherKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(libs.versions.jvm.toolchain.get().toInt())) })
}
