plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose") version kotlinVersion
    id("org.jetbrains.compose") version composeVersion
}

kotlin {

    js {
        moduleName = "njord"
        browser {
            commonWebpackConfig {
                cssSupport { enabled.set(true) } // Add this
                scssSupport { enabled.set(true) } // Add this
                outputFileName = "njord.js"
            }
            testTask {
                testLogging.showStandardStreams = true
                useKarma {
                    useChromeHeadless()
                    useFirefox()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":shared_fe"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }
    }
}

tasks.named("jsBrowserTest") {
    doFirst {
        File("webpack.config.d/dev_server_config.js")
            .renameTo(File("webpack.config.d/dev_server_config"))
    }
    doLast {
        File("webpack.config.d/dev_server_config")
            .renameTo(File("webpack.config.d/dev_server_config.js"))
    }
}
