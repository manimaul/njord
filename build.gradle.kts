
task("version") {
    doLast {
        println("version: ${project.version}")
        println("group: ${project.group}")
    }
}

/**
 * Convenience task for running the PostGIS database with docker-compose
 * eg `./gradlew :runPostGis`
 */
task<Exec>("runPostgis") {
    workingDir("chart_server_db")
    commandLine("docker-compose", "up")
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("buildImage") {
    dependsOn(":chart_server:installDist")
    commandLine("docker", "build", "-t", "ghcr.io/manimaul/njord-chart-server:${project.version}", ".")
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("publishImage") {
    dependsOn("buildImage")
    commandLine("docker", "push", "ghcr.io/manimaul/njord-chart-server:${project.version}")
}
