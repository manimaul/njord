plugins {
    id("nebula.ospackage-application") version "8.2.0"
    application
}

val nettyVersion = "4.1.36.Final"
val nettyBoringSslVersion = "2.0.25.Final"

dependencies {
    /* Netty */
    implementation("io.netty:netty-all:${nettyVersion}")
    implementation("io.netty:netty-tcnative-boringssl-static:${nettyBoringSslVersion}")
    implementation("io.netty:netty-codec-http:${nettyVersion}")

    /* Serial port IO */
    implementation("org.rxtx:rxtx:2.1.7")

    /* Project */
    implementation(project(":common"))
}

application {
    mainClassName = "io.madrona.njord.AppKt"
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
