plugins {
    kotlin("js") version "1.6.0"
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
            }
        }
    }
}

val kotlinWrappersVersion = "0.0.1-pre.271-kotlin-1.6.0"

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.0")

    //https://github.com/JetBrains/kotlin-wrappers
    implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:${kotlinWrappersVersion}"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-table")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-styled")

    implementation("org.jetbrains.kotlin-wrappers:kotlin-mui:5.1.1-pre.270-kotlin-1.6.0")

    implementation(npm("maplibre-gl", "1.15.2"))
}
