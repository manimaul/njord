plugins {
    application
    `java-library`
    `maven-publish`
}

application {
    mainClassName = "io.madrona.njord.ClientAppKt"
    applicationName = "njord-client"
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            version = "${project.version}"
            artifact(sourcesJar.get())
        }
    }
}

dependencies {
    //https://github.com/tbsalling/aismessages
    // todo (WK) implementation("dk.tbsalling:aismessages:2.2.2")

    /* Project */
    api(project(":common"))
}
