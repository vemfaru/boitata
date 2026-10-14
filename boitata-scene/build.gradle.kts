plugins {
    id("boitata.kmp.compose")
}

kotlin {
    androidLibrary {
        namespace = "br.com.faru.boitata.scene"
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":boitata-core"))
        }
    }
}
