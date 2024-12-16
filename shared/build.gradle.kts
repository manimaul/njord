plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}


group = "com.mxmariner.regatta"
version = "${properties["version"]}"


kotlin {
    jvmToolchain(17)

    jvm()

    js {
        browser()
        useEsModules()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                api(project(":geojson"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}