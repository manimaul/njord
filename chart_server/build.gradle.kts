import org.apache.tools.ant.taskdefs.condition.Os
import io.madrona.njord.build.*

plugins {
    application
    kotlin(module="jvm")
}

application {
    mainClass.set("io.madrona.njord.ChartServerAppKt")
}


dependencies {
    implementation(project(":common"))
    implementation(Deps.ktorNetty)
    implementation(Deps.ktorCore)
    implementation(Deps.ktorLocations)
    implementation(Deps.ktorWebsockets)
    implementation(Deps.ktorHostCommon)
    implementation(Deps.ktorJackson)
    implementation(Deps.logBack)
    implementation(Deps.gdal)
    implementation(Deps.geojson)

    testImplementation(platform(Deps.jupiterBom))
    testImplementation(Deps.jupiter)
    testImplementation(Deps.mockito)
    testImplementation(Deps.hamcrest)
}

tasks.named<JavaExec>("run") {
    if (Os.isFamily(Os.FAMILY_MAC)) {
        jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/gdal/3.3.1_3/lib/")
    }
}
