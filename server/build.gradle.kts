plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "io.madrona"
version = "${properties["version"]}"

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val name = "arch"
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64(name)
        hostOs == "Mac OS X" && !isArm64 -> macosX64(name)
        hostOs == "Linux" && isArm64 -> linuxArm64(name)
        hostOs == "Linux" && !isArm64 -> linuxX64(name)
        isMingwX64 -> mingwX64(name)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {

        compilations.getByName("main") {
            cinterops {
                val libssl by creating
                val libgd by creating
                val libnotify by creating
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
            }
            executable("ingest") {
                entryPoint = "io.madrona.njord.ingest.ingestMain"
                runTaskProvider?.configure {
                    argumentProviders.add(CommandLineArgumentProvider {
                        listOf(project.file("./src/nativeMain/resources").absolutePath)
                    })
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":libgdal"))
            implementation(project(":libpq"))
            implementation(project(":libzip"))
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