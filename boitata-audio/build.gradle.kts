plugins {
    id("boitata.kmp.compose")
}

kotlin {
    androidLibrary {
        namespace = "br.com.faru.boitata.audio"
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":boitata-core"))
            api(project(":boitata-audio-synth"))
        }
    }
}
