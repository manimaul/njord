plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
}

group = "io.madrona"
version = "${properties["version"]}"

repositories {
    google()
    mavenCentral()
}

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
                val libgdal by creating
            }
        }
        binaries {
            staticLib {
                baseName = "gdal"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
            implementation(project(":shared"))
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// ./gradlew publishAllPublicationsToGitHubPackagesRepository
// ./gradlew publishToMavenLocal
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/manimaul/ktgdal")
            credentials {
                username = System.getenv("GH_USER")
                password = System.getenv("GH_TOKEN")
            }
        }
    }
}
