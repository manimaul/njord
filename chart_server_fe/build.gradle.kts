plugins {
    kotlin("js") version ktVersion
    kotlin("plugin.serialization") version ktVersion
}

repositories {
    mavenCentral()
}

kotlin {
    js(LEGACY) {
        binaries.executable()
        browser {
            commonWebpackConfig {
//                cssSupport.enabled = true
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

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVersion}")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation(Deps.jsonSer)
    implementation(project(":chart_server_common"))

    //https://github.com/JetBrains/kotlin-wrappers
    implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:${Versions.kotlinWrappersVersion}"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom")
//    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-table")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled")

    //bootstrap
    implementation(npm("bootstrap", "5.1.3"))
    implementation(npm("@popperjs/core", "2.11.0"))
    implementation(npm("jquery", "3.6.0"))

    //maplibre
    implementation(npm("maplibre-gl", "2.1.9"))
}
