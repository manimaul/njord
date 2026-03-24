plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "io.madrona"
version = "${properties["version"]}"

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64" || project.findProperty("buildForArm64") == "true"
    val multiarchTuple = if (isArm64) "aarch64-linux-gnu" else "x86_64-linux-gnu"
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
                val libssl by creating {
                    if (hostOs == "Linux") {
                        compilerOpts("--sysroot=/", "-I/usr/include/$multiarchTuple", "-D__glibc_clang_prereq(a,b)=0")
                    }
                }
                val libgd by creating {
                    if (hostOs == "Linux") {
                        compilerOpts("--sysroot=/", "-I/usr/include/$multiarchTuple", "-D__glibc_clang_prereq(a,b)=0")
                    }
                }
                val libnotify by creating {
                    if (hostOs == "Linux") {
                        compilerOpts("--sysroot=/", "-I/usr/include/$multiarchTuple", "-D__glibc_clang_prereq(a,b)=0")
                    }
                }
            }
        }
        // When cross-compiling for ARM64, Debian's libcrypto.a / libcurl.a are built
        // with -moutline-atomics, emitting __aarch64_ldadd*/swp* calls that K/N's
        // bundled lld cannot resolve.  The static archives are deleted in the
        // Dockerfile so K/N falls back to the shared .so files; but as a safety net
        // we also locate ARM64 libgcc.a from the cross-toolchain and append it as a
        // positional argument to lld (after all cinterop archives, so ordering is
        // correct for archive-based symbol resolution).
        val crossGccLibGcc: String? = if (isArm64 && hostOs == "Linux") {
            try {
                val proc = ProcessBuilder("aarch64-linux-gnu-gcc", "--print-libgcc-file-name")
                    .start()
                proc.waitFor()
                proc.inputStream.bufferedReader().readLine()?.trim()
                    ?.takeIf { File(it).exists() }
            } catch (_: Exception) { null }
        } else null

        binaries {
            executable {
                entryPoint = "io.madrona.njord.main"
                if (hostOs == "Linux") {
                    linkerOpts("-L/usr/lib/$multiarchTuple")
                    crossGccLibGcc?.let { linkerOpts(it) }
                }
                runTaskProvider?.configure {
                    argumentProviders.add(CommandLineArgumentProvider {
                        listOf(project.file("./src/nativeMain/resources").absolutePath)
                    })
                }
            }
            executable("ingest") {
                entryPoint = "io.madrona.njord.ingest.ingestMain"
                if (hostOs == "Linux") {
                    linkerOpts("-L/usr/lib/$multiarchTuple")
                    crossGccLibGcc?.let { linkerOpts(it) }
                }
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
            implementation("io.ktor:ktor-server-forwarded-header:${ktorVersion}")
            implementation("io.ktor:ktor-client-curl:${ktorVersion}")
        }
        nativeTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            implementation(kotlin("test"))
        }
    }
}