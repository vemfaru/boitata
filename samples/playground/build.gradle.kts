plugins {
    id("boitata.kmp.compose")
}

kotlin {
    androidLibrary {
        namespace = "br.com.faru.boitata.playground"
    }

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":boitata-core"))
            api(project(":boitata-scene"))
            api(project(":boitata-light"))
            api(project(":boitata-weather"))
            implementation(compose.material3)
        }
    }
}
