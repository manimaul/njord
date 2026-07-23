plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {

    js {
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
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                    implementation(libs.kotlinx.coroutines.test)
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
}
