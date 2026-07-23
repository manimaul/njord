plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
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
            api(libs.compose.runtime)
            api(libs.kotlinx.coroutines.core)
        }

        val jsMain by getting {
            dependencies {
                api(libs.compose.html.core)
                api(libs.compose.html.svg)
                api(npm("maplibre-gl", libs.versions.maplibre.gl.get()))
                api(npm("bootstrap", libs.versions.bootstrap.get()))
                api(npm("@popperjs/core", libs.versions.popperjs.core.get()))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
