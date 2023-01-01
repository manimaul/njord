import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin(module = "jvm") version ktVersion
    kotlin(module = "kapt") version ktVersion
    id("io.ktor.plugin") version "2.2.1"
//    kotlin("plugin.serialization") version ktVersion
}

repositories {
    mavenCentral()
}


application {
    mainClass.set("io.madrona.njord.ChartServerAppKt")
}

//https://kotlinlang.org/docs/reference/using-gradle.html#compiler-options
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.apiVersion = "1.5"
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation(Deps.ktorNetty)
    implementation(Deps.ktorCore)
    implementation(Deps.ktorWebsockets)
    implementation(Deps.ktorHostCommon)
    implementation(Deps.ktorLogging)
    implementation(Deps.ktorStatus)
    implementation(Deps.ktorJackson)
    implementation(Deps.ktorJson)
    implementation(Deps.jacksonYaml)
    implementation(Deps.logBack)
    implementation(Deps.gdal)
    implementation(Deps.protoBuf)
    implementation(Deps.jtsCore)
    implementation(Deps.geojson)
    implementation(Deps.postgres)
    implementation(Deps.HikariCP)
    implementation(Deps.dropWizard)

    testImplementation(Deps.mockito)
    testImplementation(Deps.hamcrest)
}

tasks.named<JavaExec>("run") {
    dependsOn(":chart_server_fe:build")
    if (Os.isFamily(Os.FAMILY_MAC)) {
        jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/gdal/3.5.3/lib/")
    }
}
