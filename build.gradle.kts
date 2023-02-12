import io.madrona.njord.build.K8S

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
    dependsOn(":chart_server:clean", ":chart_server_fe:build", ":chart_server:installDist")
    commandLine("bash", "-c", "docker build -t ghcr.io/manimaul/njord-chart-server:${project.version} .")
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("publishImage") {
    commandLine("bash", "-c", "docker push ghcr.io/manimaul/njord-chart-server:${project.version}")
}

/**
 * Deploy to Kubernetes
 */
task<Exec>("deploy") {
    val yaml = K8S.chartServerDeployment(rootProject.projectDir,"${project.version}")
    commandLine("bash", "-c", "echo '${yaml}' | kubectl apply -f -")
}

/**
 * Cycle K8S Pods
 */
task<Exec>("cyclePods") {
    commandLine("bash", "-c", "kubectl -n njord delete pods -l app=njord-chart-svc")
}

/**
 * Builds container image, deploys image to registry and deploys changes to Kubernetes
 * eg `./gradlew :buildPublishDeploy`
 */
tasks.register<GradleBuild>("buildPublishDeploy") {
    tasks = listOf(":buildImage", ":publishImage", ":deploy", ":cyclePods")
}
