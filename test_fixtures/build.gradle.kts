// Test-only support library: locates testdata/ and reads/writes golden fixtures in
// verify or regenerate mode. See testdata/README.md and docs/RUST_PORT_PLAN.md §5.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.madrona"
version = "${properties["version"]}"

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64" || project.findProperty("buildForArm64") == "true"
    val isMingwX64 = hostOs.startsWith("Windows")
    val name = "arch"
    when {
        hostOs == "Mac OS X" -> macosArm64(name)
        hostOs == "Linux" && isArm64 -> linuxArm64(name)
        hostOs == "Linux" && !isArm64 -> linuxX64(name)
        isMingwX64 -> mingwX64(name)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(project(":shared"))
            api(libs.kotlinx.serialization.json)
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
