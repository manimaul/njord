plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "io.madrona"
version = "1.0-SNAPSHOT"

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {

        compilations.getByName("main") {
            cinterops {
                val libssl by creating
            }
        }
        binaries {
            executable {
                entryPoint = "io.madrona.njord.main"
                runTaskProvider?.configure {
                    argumentProviders.add(CommandLineArgumentProvider {
                        listOf(project.file("./src/nativeMain/resources").absolutePath)
                    })
                }
//                linkerOpts("-Wl,--allow-shlib-undefined")
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":shared"))
//            implementation("io.madrona:ktpq:1.0-SNAPSHOT")
            implementation(project(":libgdal"))
            implementation(project(":libpq"))
            implementation("io.ktor:ktor-server-core:${ktorVersion}")
            implementation("io.ktor:ktor-server-cio:${ktorVersion}")
            implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
            implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
            implementation("io.ktor:ktor-server-auth:${ktorVersion}")
            implementation("io.ktor:ktor-server-cors:${ktorVersion}")
            implementation("io.ktor:ktor-server-websockets:${ktorVersion}")
            implementation("io.ktor:ktor-server-host-common:${ktorVersion}")
            implementation("io.ktor:ktor-client-curl:${ktorVersion}")
        }
        nativeTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation(kotlin("test"))
        }
    }
}