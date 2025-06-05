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
        compilations.getByName("main") {
            cinterops {
                val libpq by creating
            }
        }
        binaries {
            staticLib {
                baseName = "pq"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
        }

        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}