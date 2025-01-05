import io.madrona.njord.build.*
import io.madrona.njord.build.GitInfo.gitBranch
import io.madrona.njord.build.GitInfo.gitShortHash
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import java.util.*

plugins {
    kotlin("jvm") version kotlinVersion apply false
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.compose") version kotlinVersion apply false
    id("org.jetbrains.compose") version composeVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.android.library") version agpVersion apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task("version") {
    doLast {
        println("version: ${project.version}")
        println("group: ${project.group}")
        println("git branch: ${gitBranch()}")
        println("git hash: ${gitShortHash()}")
    }
}

/**
 * Writes k8s secret with CHART_SERVER_OPTS (adminKey, adminUser, adminPass)
 */
task("secret") {
    doLast {
        println(k8sApplySecret())
    }
}

/**
 * Shows GPG encrypted secrets in [Secret]
 */
task("showSecret") {
    doLast {
        println("admin key: ${adminKey()}")
        println("admin user: ${user()}")
        println("admin pass: ${password()}")
    }
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("makeImg") {
    dependsOn(":web:jsBrowserDistribution", ":chart_server:installDist")
    mustRunAfter(":web:jsBrowserDistribution", ":chart_server:installDist")
    commandLine("bash", "-c", "docker build -t ghcr.io/manimaul/njord-chart-server:${project.version} .")
}

/**
 * Build the container image. This much faster than a multi-stage docker build.
 * eg `./gradlew :buildImage`
 */
task<Exec>("pubImg") {
    dependsOn(":makeImg")
    mustRunAfter(":makeImg")
    commandLine("bash", "-c", "docker push ghcr.io/manimaul/njord-chart-server:${project.version}")
}

/**
 * Deploy to Kubernetes
 */
task<Exec>("k8sApply") {
    mustRunAfter(":pubImg")
    val yaml = K8S.chartServerDeploymentWrite(rootProject.projectDir, "${project.version}")
    commandLine("bash", "-c", "kubectl apply -f '${yaml.absolutePath}'")
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

tasks.findByPath(":web:jsBrowserProductionWebpack")?.let { it as? KotlinWebpack }?.apply {
    doFirst {
        File("web/webpack.config.d/dev_server_config.js")
            .renameTo(File("web/webpack.config.d/dev_server_config"))
    }
    doLast {
        File("web/webpack.config.d/dev_server_config")
            .renameTo(File("web/webpack.config.d/dev_server_config.js"))
    }
}
