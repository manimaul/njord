plugins {
    application
}

repositories {
    maven { url = uri("https://dl.bintray.com/madrona/maven") }
    jcenter()
}

dependencies {
    implementation("com.willkamp:vial-server:0.0.7")
}