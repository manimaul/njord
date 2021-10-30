import org.apache.tools.ant.taskdefs.condition.Os
import io.madrona.njord.build.*

plugins {
    application
    kotlin(module="jvm")
}

application {
    mainClass.set("io.madrona.njord.ChartServerAppKt")
}

//val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
//val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
//
//compileKotlin.kotlinOptions {
//    jvmTarget = "11"
//}
//compileTestKotlin.kotlinOptions {
//    jvmTarget = "11"
//}

dependencies {
    implementation(project(":common"))
    implementation(Deps.ktorNetty)
    implementation(Deps.ktorCore)
    implementation(Deps.ktorLocations)
    implementation(Deps.ktorWebsockets)
    implementation(Deps.ktorHostCommon)
    implementation(Deps.ktorJackson)
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
    if (Os.isFamily(Os.FAMILY_MAC)) {
        jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/gdal/3.3.2_3/lib/")
    }
}
