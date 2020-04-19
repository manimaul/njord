plugins {
    `java-library`
    `maven-publish`
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
