plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose") version kotlinVersion
    id("org.jetbrains.compose") version composeVersion
    kotlin("plugin.serialization")
}

version = "${properties["version"]}"

kotlin {
    js {
        browser()
        useCommonJs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api("org.jetbrains.compose.runtime:runtime:${composeVersion}")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }

        val jsMain by getting {
            dependencies {
                api("org.jetbrains.compose.html:html-core:${composeVersion}")
                api("org.jetbrains.compose.html:html-svg:${composeVersion}")
                api(npm("maplibre-gl", "4.7.1"))
                api(npm("bootstrap", "5.3.3"))
                api(npm("@popperjs/core", "2.11.8"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
