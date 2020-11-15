plugins {
    application
}

repositories {
    maven { url = uri("https://dl.bintray.com/madrona/maven") }
    jcenter()
}

dependencies {
    implementation("com.willkamp:vial-server:0.0.7")
    implementation("org.xerial:sqlite-jdbc:3.31.1")
    implementation("org.gdal:gdal:2.4.0")
    implementation("org.locationtech.jts:jts-core:1.16.1")
    implementation("com.google.guava:guava:29.0-jre")
    implementation("org.locationtech.proj4j:proj4j:1.1.1")
    implementation("mil.nga.sf:sf-geojson:2.0.4")
}

tasks.named<JavaExec>("run") {
//    jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/osgeo-gdal/3.1.0_1/lib/")
//    jvmArgs = listOf("-Djava.library.path=/usr/local/Cellar/osgeo-gdal/3.1.0_1/lib/")
}
