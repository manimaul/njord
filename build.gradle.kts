import io.madrona.njord.build.K8S
import java.util.UUID

task("version") {
    doLast {
        println("version: ${project.version}")
        println("group: ${project.group}")
    }
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("makeImg") {
    dependsOn(":chart_server_fe:build", ":chart_server:installDist")
    commandLine("bash", "-c", "docker build -t ghcr.io/manimaul/njord-chart-server:${project.version} .")
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("pubImg") {
    mustRunAfter(":makeImg")
    commandLine("bash", "-c", "docker push ghcr.io/manimaul/njord-chart-server:${project.version}")
}

/**
 * Deploy to Kubernetes
 */
task<Exec>("k8sApply") {
    mustRunAfter(":pubImg")
    val adminKey = if (project.hasProperty("adminKey")) {
        "${project.property("adminKey")}"
    } else {
        UUID.randomUUID().toString()
    }
    val yaml = K8S.chartServerDeployment(rootProject.projectDir, "${project.version}", adminKey)
    commandLine("bash", "-c", "echo '${yaml}' | kubectl apply -f -")
}

/**
 * Cycle K8S Pods
 */
task<Exec>("cyclePods") {
    mustRunAfter(":k8sApply", ":pubImg", ":holdOn")
    commandLine("bash", "-c", "kubectl -n njord delete pods -l app=njord-chart-svc")
}

/**
 * Cycle K8S Pods
 */
task<Exec>("holdOn") {
    mustRunAfter(":pubImg")
    commandLine("bash", "-c", "echo 'hold on' && sleep 5")
}

/**
 * Builds container image, deploys image to registry and deploys changes to Kubernetes
 * eg `./gradlew :buildPublishDeploy`
 */
tasks.register<GradleBuild>("deploy") {
    tasks = listOf(":makeImg", ":pubImg", ":k8sApply", ":holdOn", ":cyclePods")
}
