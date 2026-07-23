plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("maven-publish")
}

group = "io.madrona"
version = "${properties["version"]}"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64" || project.findProperty("buildForArm64") == "true"
    val multiarchTuple = if (isArm64) "aarch64-linux-gnu" else "x86_64-linux-gnu"
    val isMingwX64 = hostOs.startsWith("Windows")
    val name = "arch"
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosArm64(name)
        hostOs == "Linux" && isArm64 -> linuxArm64(name)
        hostOs == "Linux" && !isArm64 -> linuxX64(name)
        isMingwX64 -> mingwX64(name)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        compilations.all {
            cinterops {
                val libpq by creating {
                    if (hostOs == "Linux") {
                        compilerOpts("--sysroot=/", "-I/usr/include/$multiarchTuple", "-D__glibc_clang_prereq(a,b)=0")
                    }
                }
            }
        }
        binaries {
            staticLib {
                baseName = "pq"
            }
            if (hostOs == "Linux") {
                getTest("DEBUG").linkerOpts("-L/usr/lib/$multiarchTuple")
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }

        nativeTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
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
            url = uri("https://maven.pkg.github.com/manimaul/ktpq")
            credentials {
                username = System.getenv("GH_USER")
                password = System.getenv("GH_TOKEN")
            }
        }
    }
}
