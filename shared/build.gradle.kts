plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}


group = "com.mxmariner.regatta"
version = "${properties["version"]}"


apply<VersionPlugin>()

configure<VersionPluginExtension> {
    versionOutDir.set(layout.projectDirectory.dir("src/commonMain/kotlin"))
}

kotlin {
    jvmToolchain(17)

    jvm()

    js {
        browser()
        useEsModules()
    }

    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64" || project.findProperty("buildForArm64") == "true"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64()
        hostOs == "Mac OS X" && !isArm64 -> macosX64()
        hostOs == "Linux" && isArm64 -> linuxArm64()
        hostOs == "Linux" && !isArm64 -> linuxX64()
        isMingwX64 -> mingwX64()
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            staticLib {
                baseName = "shared"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
        }
        val commonMain by getting {
            dependencies {
                api(project(":geojson"))
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
