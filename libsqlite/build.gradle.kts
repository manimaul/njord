plugins {
    kotlin("multiplatform")
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
        compilations.getByName("main") {
            cinterops {
                val libsqlite by creating {
                    if (hostOs == "Linux") {
                        compilerOpts("--sysroot=/", "-I/usr/include/$multiarchTuple", "-D__glibc_clang_prereq(a,b)=0")
                    }
                }
            }
        }
        binaries {
            staticLib {
                baseName = "sqlite"
            }
        }
    }

    sourceSets {
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
