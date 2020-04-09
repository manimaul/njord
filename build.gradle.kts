plugins {
    id("java")
    kotlin("jvm") version "1.3.71"
    id("nebula.ospackage-application") version "8.2.0"
    kotlin("kapt") version "1.3.71"
    application
}

repositories {
    jcenter()
}

val junitVersion = "5.4.2"
val nettyVersion = "4.1.36.Final"
val nettyBoringSslVersion = "2.0.25.Final"
val rxJavaVersion = "2.2.12"
val slf4jVersion = "1.7.25"
val logbackVersion = "1.2.3"
val typesafeConfigVersion = "1.3.3"
val groovyVersion = "2.5.6"
val lombokVersion = "1.18.6"
val daggerVersion = "2.24"

dependencies {
    /* Kotlin */
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    /* Netty */
    implementation("io.netty:netty-all:${nettyVersion}")
    implementation("io.netty:netty-tcnative-boringssl-static:${nettyBoringSslVersion}")
    implementation("io.netty:netty-codec-http:${nettyVersion}")

    /* RxJava */
    implementation("io.reactivex.rxjava2:rxjava:${rxJavaVersion}")

    /* Config */
    implementation("com.typesafe:config:${typesafeConfigVersion}")

    /* Logging */
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")
    implementation("ch.qos.logback:logback-core:${logbackVersion}")
    implementation("org.codehaus.groovy:groovy-all:${groovyVersion}")

    /* Dagger 2*/
    implementation("com.google.dagger:dagger:${daggerVersion}")
    kapt("com.google.dagger:dagger-compiler:${daggerVersion}")
    kaptTest("com.google.dagger:dagger-compiler:${daggerVersion}")

    /* Serial port IO */
    implementation("org.rxtx:rxtx:2.1.7")
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
