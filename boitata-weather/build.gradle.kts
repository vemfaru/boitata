plugins {
    id("boitata.kmp.compose")
}

kotlin {
    androidLibrary {
        namespace = "br.com.faru.boitata.weather"
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":boitata-core"))
        }
    }
}
