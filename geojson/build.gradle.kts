plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
}


group = "io.madrona"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    jvm {
    }

    js {
        browser()
        useEsModules()
    }

    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// ./gradlew publishKotlinMultiplatformPublicationToGitHubPackagesRepository
// ./gradlew publishAllPublicationsToGitHubPackagesRepository
// ./gradlew publishToMavenLocal

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/manimaul/geojson")
            credentials {
                username = System.getenv("GH_USER")
                password = System.getenv("GH_TOKEN")
            }
        }
    }
}
