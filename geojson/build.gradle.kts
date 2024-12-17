plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}


group = "io.madrona.geojson"
version = "${properties["version"]}"


kotlin {
    jvmToolchain(17)

    jvm {
        withJava()
    }

    js {
        browser()
        useEsModules()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
