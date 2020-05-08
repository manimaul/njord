plugins {
    id("nebula.ospackage-application") version "8.2.0"
    application
}

val nettyVersion = "4.1.36.Final"
val nettyBoringSslVersion = "2.0.25.Final"
val logbackVersion = "1.2.3"
val typesafeConfigVersion = "1.3.3"
val groovyVersion = "2.5.6"

dependencies {
    /* Netty */
    implementation("io.netty:netty-all:${nettyVersion}")
    implementation("io.netty:netty-tcnative-boringssl-static:${nettyBoringSslVersion}")
    implementation("io.netty:netty-codec-http:${nettyVersion}")

    /* Serial port IO */
    implementation("org.rxtx:rxtx:2.1.7")

    /* Config */
    implementation("com.typesafe:config:${typesafeConfigVersion}")

    /* Logging */
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")
    implementation("ch.qos.logback:logback-core:${logbackVersion}")
    implementation("org.codehaus.groovy:groovy-all:${groovyVersion}")


    /* Project */
    implementation(project(":common"))
    implementation(project(":client"))
}

application {
    mainClassName = "io.madrona.njord.ServerAppKt"
    applicationName = "njord"
}

//tasks.named<JavaExec>("run") {
//    jvmArgs = listOf("-Djava.library.path=${RXTX}/rxtx-2.1-7r2/i686-apple-darwin19.3.0")
//}

ospackage {
    packageName = "njord"
    version = "${project.version}"
    release = "1"
    from("debpkg/njord.service", closureOf<CopySpec> {
        into("/etc/systemd/system/")
    })
    from("debpkg/njord_exec.sh", closureOf<CopySpec> {
        into("/usr/bin/")
    })
    from("debpkg/njord.conf", closureOf<CopySpec> {
        into("/etc/")
    })
    preDepends("systemd")
    requires("openjdk-11-jre-headless")
    requires("librxtx-java")
    postInstall(file("debpkg/postInstall.sh"))
    preUninstall(file("debpkg/preUninstall.sh"))
    postUninstall(file("debpkg/postUninstall.sh"))
}
