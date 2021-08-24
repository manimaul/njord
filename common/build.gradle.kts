import io.madrona.njord.build.*

plugins {
    `java-library`
    `maven-publish`
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.also {
        it.convention("sources")
        it.set("sources")
    }
    from(sourceSets.main.get().allSource)
}

dependencies {
    api(Deps.rxJava)
    api(Deps.slf4j)
    api(Deps.dagger)
    //kapt(Deps.daggerCompiler)
    //kaptTest(Deps.daggerCompiler)
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
