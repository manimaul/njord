const val ktVersion = "1.6.21"

object Versions {
    const val kotlinVersion = "1.6.21"
    const val ktorVersion = "1.6.3"
    const val logbackVersion = "1.2.3"
    const val hamcrestVersion="1.3"
    const val mockitoVersion="2.18.3"
    const val dropWizardVersion="4.2.4"
    const val jacksonVersion="2.13.0"

    val kotlinWrappersVersion = "0.0.1-pre.332-kotlin-1.6.21"
}

object Deps {
    const val gdal = "org.gdal:gdal:3.3.0"
    const val jtsCore = "org.locationtech.jts:jts-core:1.18.2"
    const val protoBuf = "com.google.protobuf:protobuf-java:3.18.1"
    const val geojson = "mil.nga.sf:sf-geojson:2.0.4"
    const val ktorWebsockets = "io.ktor:ktor-websockets:${Versions.ktorVersion}"
    const val ktorCore = "io.ktor:ktor-server-core:${Versions.ktorVersion}"
    const val ktorHostCommon = "io.ktor:ktor-server-host-common:${Versions.ktorVersion}"
    const val ktorLocations = "io.ktor:ktor-locations:${Versions.ktorVersion}"
    const val ktorNetty = "io.ktor:ktor-server-netty:${Versions.ktorVersion}"
    const val logBack = "ch.qos.logback:logback-classic:${Versions.logbackVersion}"
    const val ktorJackson = "io.ktor:ktor-jackson:${Versions.ktorVersion}"
    const val ktorJson = "io.ktor:ktor-serialization:${Versions.ktorVersion}"

    const val jacksonYaml = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${Versions.jacksonVersion}"
    const val jacksonCsv = "com.fasterxml.jackson.dataformat:jackson-dataformat-csv:${Versions.jacksonVersion}"
    const val postgres = "org.postgresql:postgresql:42.2.23.jre7"
    const val HikariCP = "com.zaxxer:HikariCP:5.0.0"

    const val mockito = "org.mockito:mockito-core:${Versions.mockitoVersion}"
    const val hamcrest = "org.hamcrest:hamcrest-all:${Versions.hamcrestVersion}"
    const val dropWizard = "io.dropwizard.metrics:metrics-core:${Versions.dropWizardVersion}"

    const val jsonSer = "org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2"
}
