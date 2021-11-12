package io.madrona.njord.build

const val daggerVersion = "2.24"
const val vialVersion = "2.0.3-SNAPSHOT"
const val ktorVersion = "1.6.3"
const val logbackVersion = "1.2.3"
const val hamcrestVersion="1.3"
const val mockitoVersion="2.18.3"
const val dropWizardVersion="4.2.4"

object Deps {
    const val dagger = "com.google.dagger:dagger:$daggerVersion"
    const val daggerCompiler = "com.google.dagger:dagger-compiler:$daggerVersion"
    const val vial = "com.willkamp:vial-server:$vialVersion"
    const val gdal = "org.gdal:gdal:3.3.0"
    const val jtsCore = "org.locationtech.jts:jts-core:1.18.2"
    const val protoBuf = "com.google.protobuf:protobuf-java:3.18.1"
    const val geojson = "mil.nga.sf:sf-geojson:2.0.4"
    const val slf4j = "org.slf4j:slf4j-api:1.7.25"
    const val ktorWebsockets = "io.ktor:ktor-websockets:$ktorVersion"
    const val ktorCore = "io.ktor:ktor-server-core:$ktorVersion"
    const val ktorHostCommon = "io.ktor:ktor-server-host-common:$ktorVersion"
    const val ktorLocations = "io.ktor:ktor-locations:$ktorVersion"
    const val ktorNetty = "io.ktor:ktor-server-netty:$ktorVersion"
    const val logBack = "ch.qos.logback:logback-classic:$logbackVersion"
    const val ktorJackson = "io.ktor:ktor-jackson:$ktorVersion"
    const val jacksonYaml = "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0"
    const val postgres = "org.postgresql:postgresql:42.2.23.jre7"
    const val HikariCP = "com.zaxxer:HikariCP:5.0.0"

    const val mockito = "org.mockito:mockito-core:$mockitoVersion"
    const val hamcrest = "org.hamcrest:hamcrest-all:$hamcrestVersion"
    const val dropWizard = "io.dropwizard.metrics:metrics-core:$dropWizardVersion"
}
