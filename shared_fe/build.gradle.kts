plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose") version kotlinVersion
    id("org.jetbrains.compose") version composeVersion
    id("com.android.library")
}

version = "${properties["version"]}"

kotlin {
    androidTarget()
    js {
        browser()
        useCommonJs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(compose.runtime)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }

        val jsMain by getting {
            dependencies {
                api(compose.html.core)
                api(compose.html.svg)
                api(npm("maplibre-gl", "4.7.1"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    compileSdk = 35
    namespace = "com.openenc"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
