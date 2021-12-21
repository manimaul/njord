plugins {
    kotlin("js") version "1.6.0"
    kotlin("plugin.serialization") version "1.6.0"
}

repositories {
    mavenCentral()
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
                outputFileName = "njord.js"
            }
            val envTargetWebpackArgs = listOf("--env", "njordVersion='${project.version}'")
            webpackTask {
                args.plusAssign(envTargetWebpackArgs)
                webpackConfigApplier {
                    export = false // stops default export of config object in Webpack code
                }
            }
        }
    }
}

val kotlinWrappersVersion = "0.0.1-pre.275-kotlin-1.6.0"

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    implementation(project(":chart_server_common"))

    //https://github.com/JetBrains/kotlin-wrappers
    implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:${kotlinWrappersVersion}"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-table")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled")

    //bootstrap
    implementation(npm("bootstrap", "5.1.3"))
    implementation(npm("@popperjs/core", "2.11.0"))
    implementation(npm("jquery", "3.6.0"))

    //maplibre
    implementation(npm("maplibre-gl", "1.15.2"))
}
