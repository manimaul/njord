import io.madrona.njord.build.GitInfo
import io.madrona.njord.build.VersionPlugin

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version ktorVersion
    id("com.netflix.nebula.ospackage-application") version osPackageVersion
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

ospackage {
    packageName = "njord"
    version = "${project.version}-${GitInfo.gitShortHash()}"
    release = "1"
    from("debpkg/gdal-arm64", closureOf<CopySpec> {
        into("/opt/chart_server/gdal")
    })
    from("debpkg/njord.service", closureOf<CopySpec> {
        into("/etc/systemd/system/")
    })
    from("debpkg/njord_exec.sh", closureOf<CopySpec> {
        into("/usr/bin/")
    })
    from("debpkg/njord.conf", closureOf<CopySpec> {
        into("/etc/")
    })
    from("build/dist/js/productionExecutable", closureOf<CopySpec> {
        into("/opt/chart_server/public")
    })
    from("debpkg/njord_setup.sh", closureOf<CopySpec> {
        into("/opt/chart_server")
    })
    preDepends("systemd")
    preDepends("postgresql")
    preDepends("postgis")
    preDepends("memcached")
    preDepends("postgresql-client")
    requires("openjdk-17-jre-headless")
    postInstall(file("debpkg/postInstall.sh"))
    preUninstall(file("debpkg/preUninstall.sh"))
    postUninstall(file("debpkg/postUninstall.sh"))
}


task<Copy>("debWebRes") {
    dependsOn(":web:jsBrowserDistribution")
    mustRunAfter(":web:jsBrowserDistribution")
    from("../web/build/dist/js/productionExecutable")
    into("build/dist/js/productionExecutable")
}

tasks.findByName("buildDeb")?.apply {
    dependsOn("debWebRes")
    mustRunAfter("debWebRes")
}

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
                implementation(project(":shared"))
                implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
                implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
                implementation("ch.qos.logback:logback-classic:$logbackVersion")
                implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-server-compression:$ktorVersion")

                implementation("io.ktor:ktor-server-websockets-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-host-common-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-call-logging-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-status-pages:${ktorVersion}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation-jvm:${ktorVersion}")
                implementation("io.ktor:ktor-server-cors:${ktorVersion}")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
                implementation("io.ktor:ktor-client-okhttp-jvm:$ktorVersion")
                implementation("no.ecc.vectortile:java-vector-tile:1.3.23")

                implementation(files("libs/gdal-3.10.0.jar"))
                implementation("ch.qos.logback:logback-classic:1.4.12")
                implementation("com.google.protobuf:protobuf-java:3.22.3")
                implementation("org.locationtech.jts:jts-core:1.19.0")
                implementation("org.postgresql:postgresql:42.7.3")
                implementation("com.zaxxer:HikariCP:5.0.1")
                implementation("io.ktor:ktor-server-metrics:$ktorVersion")
                implementation("io.dropwizard.metrics:metrics-core:4.2.29")
                implementation("io.dropwizard.metrics:metrics-jmx:4.2.29")
                implementation("com.googlecode.xmemcached:xmemcached:2.4.8")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.mockito:mockito-core:5.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
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
            dependsOn(":web:jsBrowserDistribution")
        }
        jvmArgs = listOf("-Djava.library.path=/opt/gdal/share/java")
    }
}
