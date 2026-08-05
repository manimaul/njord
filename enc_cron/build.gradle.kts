plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
        hostOs == "Mac OS X" -> macosArm64(name)
        hostOs == "Linux" && isArm64 -> linuxArm64(name)
        hostOs == "Linux" && !isArm64 -> linuxX64(name)
        isMingwX64 -> mingwX64(name)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        // No cinterops of its own - libexpat and libzip are consumed as modules.

        // See server/build.gradle.kts for why ARM64 cross-builds need libgcc.a appended.
        val crossGccLibGcc: String? = if (isArm64 && hostOs == "Linux") {
            try {
                val proc = ProcessBuilder("aarch64-linux-gnu-gcc", "--print-libgcc-file-name")
                    .start()
                proc.waitFor()
                proc.inputStream.bufferedReader().readLine()?.trim()
                    ?.takeIf { File(it).exists() }
            } catch (_: Exception) {
                null
            }
        } else null

        binaries {
            executable {
                entryPoint = "io.madrona.njord.enccron.main"
                // cinterop linkerOpts from dependency modules do not propagate to the final link
                // step, so -lexpat / -lzip need their search path set here on the binary itself.
                if (hostOs == "Linux") {
                    linkerOpts("-L/usr/lib/$multiarchTuple", "--allow-shlib-undefined")
                    crossGccLibGcc?.let { linkerOpts(it) }
                }
                runTaskProvider?.configure {
                    argumentProviders.add(CommandLineArgumentProvider {
                        // Positional resources dir supplies config/enc_cron.json, which has the
                        // required fields. -PencCronArgs appends the flags (--dry-run, --from-file).
                        listOf(project.file("./src/nativeMain/resources").absolutePath) +
                            ((project.findProperty("encCronArgs") as String?)
                                ?.split(" ")?.filter { it.isNotBlank() } ?: emptyList())
                    })
                    // Exec inherits the daemon env, not the invoking shell's - wire the override
                    // through a property so -PencCronOpts=... actually reaches the process.
                    (project.findProperty("encCronOpts") as String?)?.let {
                        environment("ENC_CRON_OPTS", it)
                    }
                }
            }
            if (hostOs == "Linux") {
                getTest("DEBUG").linkerOpts("-L/usr/lib/$multiarchTuple", "--allow-shlib-undefined")
                crossGccLibGcc?.let { getTest("DEBUG").linkerOpts(it) }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":libexpat"))
            implementation(project(":libzip"))
            implementation(libs.ktor.client.curl)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
