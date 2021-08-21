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
    implementation(Deps.vial)
    implementation(Deps.sqliteJdbc)
    implementation(Deps.gdal)
    implementation(Deps.jtsCore)
    implementation(Deps.guava)
    implementation(Deps.proj4)
    implementation(Deps.geojson)
}

tasks.named<JavaExec>("run") {
    if (Os.isFamily(Os.FAMILY_MAC)) {
        jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/gdal/3.3.1_3/lib/")
    }
}
