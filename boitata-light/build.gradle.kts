plugins {
    id("boitata.kmp.compose")
}

kotlin {
    androidLibrary {
        namespace = "br.com.faru.boitata.light"
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":boitata-core"))
            api(project(":boitata-scene"))
        }
    }
}
