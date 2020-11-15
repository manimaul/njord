import java.net.URI

plugins {
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation("com.google.guava:guava:29.0-jre")
}

task<Exec>("fetchThemeXml") {
    val xml = URI("https://raw.githubusercontent.com/OpenCPN/OpenCPN/master/data/s57data/chartsymbols.xml")
    commandLine(*curlCommand(xml))
}

fun curlCommand(uri: URI) : Array<String> {
    val name = uri.path.split("/").last()
    val f = File("./${project.name}/src/main/resources/${name}")
    return arrayOf("curl", "-o", f.absolutePath, uri.toString())
}
