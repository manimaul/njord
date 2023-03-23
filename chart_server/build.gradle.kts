import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin(module = "jvm") version ktVersion
    kotlin(module = "kapt") version ktVersion
    id("io.ktor.plugin") version ktorVersion
}

repositories {
    mavenCentral()
}


application {
    mainClass.set("io.madrona.njord.ChartServerAppKt")
}

//https://kotlinlang.org/docs/reference/using-gradle.html#compiler-options
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.apiVersion = "1.5"
}

tasks.compileJava {
   targetCompatibility = "17"
   sourceCompatibility = "17"
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    implementation(files("libs/gdal-3.6.2.jar"))
    implementation("io.ktor:ktor-server-netty-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-core-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-websockets-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-host-common:${ktorVersion}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-jackson-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${ktorVersion}")
    implementation("io.ktor:ktor-server-cors:${ktorVersion})")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    implementation("com.google.protobuf:protobuf-java:3.19.6")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("mil.nga.sf:sf-geojson:3.3.2")
    implementation("org.postgresql:postgresql:42.5.4")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("io.dropwizard.metrics:metrics-core:4.2.17")

    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.hamcrest:hamcrest-all:1.3")
}

tasks {
    test {
        jvmArgs = listOf("-Djava.library.path=/opt/gdal/share/java")
    }
}

tasks.named<JavaExec>("run") {
    //./gradlew :chart_server:run -Pskip
    if (project.hasProperty("skip")) {
        println("skipping web build")
    } else {
        dependsOn(":chart_server_fe:build")
    }
    if (Os.isFamily(Os.FAMILY_MAC)) {
        jvmArgs = listOf("-Djava.library.path=/opt/gdal/share/java")
    }
}
