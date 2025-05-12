plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

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
        binaries {
            executable {
                entryPoint = "main"
                runTask?.run {
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
            implementation(project(":libpq"))
            implementation(project(":libgdal"))
            implementation(project(":libgeos"))
            implementation("io.ktor:ktor-server-core:${ktorVersion}")
            implementation("io.ktor:ktor-server-cio:${ktorVersion}")
            implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
            implementation("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
            implementation("io.ktor:ktor-server-auth:${ktorVersion}")
//            implementation("io.ktor:ktor-server-compression:${ktorVersion}")
            implementation("io.ktor:ktor-server-cors:${ktorVersion}")
            implementation("io.ktor:ktor-server-websockets:${ktorVersion}")
            implementation("io.ktor:ktor-server-host-common:${ktorVersion}")
//            implementation("io.ktor:ktor-server-call-logging:${ktorVersion}")
            implementation("io.ktor:ktor-client-curl:${ktorVersion}")
        }
    }
}