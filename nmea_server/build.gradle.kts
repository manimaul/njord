import io.madrona.njord.build.*

plugins {
    id("nebula.ospackage-application") version "8.5.6"
    application
}

dependencies {
    implementation(Deps.vial)
    implementation(Deps.dagger)
    implementation(Deps.rxtx)
    implementation(project(":common"))
    implementation(project(":nmea_client"))

    kapt(Deps.daggerCompiler)
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
