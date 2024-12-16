import io.madrona.njord.build.VersionPlugin
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    kotlin("multiplatform")
    id("io.ktor.plugin") version ktorVersion
}

repositories {
    mavenCentral()
    maven(url = "https://maven.ecc.no/releases")
}

apply<VersionPlugin>()

application {
    mainClass.set("io.madrona.njord.ChartServerAppKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

group = "io.madrona.njord"
version = "${properties["version"]}"

kotlin {
    jvmToolchain(17)

    jvm {
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                jvmArgs = listOf("-Djava.library.path=/opt/gdal/jni")
            }
        }
    }

    sourceSets {
        val jvmMain by getting {
            kotlin.srcDir("build/generated/source/version")
            dependencies {
                implementation("io.ktor:ktor-server-core-jvm")
                implementation("io.ktor:ktor-server-netty-jvm")
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
                implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-server-compression:$ktorVersion")

                implementation("io.ktor:ktor-server-websockets-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-host-common-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-call-logging-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
                implementation("io.ktor:ktor-serialization-jackson-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-cors:${ktorVersion}")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("io.ktor:ktor-client-okhttp-jvm:$ktorVersion")
                implementation("no.ecc.vectortile:java-vector-tile:1.3.23")

                implementation(files("libs/gdal-3.10.0.jar"))
                implementation("ch.qos.logback:logback-classic:1.4.12")
                implementation("com.google.protobuf:protobuf-java:3.19.6")
                implementation("org.locationtech.jts:jts-core:1.19.0")
                implementation("mil.nga.sf:sf-geojson:3.3.2")
                implementation("org.postgresql:postgresql:42.7.3")
                implementation("com.zaxxer:HikariCP:5.0.1")
                implementation("io.dropwizard.metrics:metrics-core:4.2.17")
                implementation("com.googlecode.xmemcached:xmemcached:2.4.8")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-server-tests-jvm")
                implementation(kotlin("test"))
                implementation("org.mockito:mockito-core:5.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
}

tasks {
    named<JavaExec>("run") {
        //./gradlew :chart_server:run -Pskip
        if (project.hasProperty("skip")) {
            println("skipping web build")
        } else {
            dependsOn(":chart_server_fe:build")
        }
        jvmArgs = listOf("-Djava.library.path=/opt/gdal/share/java")
    }
}
