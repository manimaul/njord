const val ktVersion = "1.8.0"

object Versions {
    const val kotlinVersion = "1.8.0"
    const val ktorVersion = "2.2.1"
    const val logbackVersion = "1.2.3"
    const val hamcrestVersion="1.3"
    const val mockitoVersion="2.18.3"
    const val dropWizardVersion="4.2.4"
    const val jacksonVersion="2.13.0"
}

object Deps {
    const val gdal = "org.gdal:gdal:3.3.0"
    const val jtsCore = "org.locationtech.jts:jts-core:1.18.2"
    const val protoBuf = "com.google.protobuf:protobuf-java:3.18.1"
    const val geojson = "mil.nga.sf:sf-geojson:2.0.4"
    const val ktorWebsockets = "io.ktor:ktor-server-websockets-jvm:${Versions.ktorVersion}"
    const val ktorCore = "io.ktor:ktor-server-core-jvm:${Versions.ktorVersion}"
    const val ktorHostCommon = "io.ktor:ktor-server-host-common:${Versions.ktorVersion}"
    const val ktorNetty = "io.ktor:ktor-server-netty-jvm:${Versions.ktorVersion}"
    const val ktorLogging = "io.ktor:ktor-server-call-logging-jvm:${Versions.ktorVersion}"
    const val ktorCors = "io.ktor:ktor-server-cors:${Versions.ktorVersion})"
    const val ktorStatus = "io.ktor:ktor-server-status-pages:${Versions.ktorVersion}"
    const val logBack = "ch.qos.logback:logback-classic:${Versions.logbackVersion}"
    const val ktorJackson = "io.ktor:ktor-serialization-jackson-jvm:${Versions.ktorVersion}"
    const val ktorJson = "io.ktor:ktor-server-content-negotiation-jvm:${Versions.ktorVersion}"

    const val jacksonYaml = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${Versions.jacksonVersion}"
    const val postgres = "org.postgresql:postgresql:42.2.23.jre7"
    const val HikariCP = "com.zaxxer:HikariCP:5.0.0"

    const val mockito = "org.mockito:mockito-core:${Versions.mockitoVersion}"
    const val hamcrest = "org.hamcrest:hamcrest-all:${Versions.hamcrestVersion}"
    const val dropWizard = "io.dropwizard.metrics:metrics-core:${Versions.dropWizardVersion}"
}
