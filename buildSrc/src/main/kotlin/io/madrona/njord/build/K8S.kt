package io.madrona.njord.build

import java.io.File

object K8S {

    fun chartServerDeployment(version: String): String {
        return File("k8s_deploy/chart_server.yaml")
            .inputStream()
            .readBytes()
            .toString(Charsets.UTF_8)
            .replace(
                "ghcr.io/manimaul/njord-chart-server:latest",
                "ghcr.io/manimaul/njord-chart-server:$version"
            )
    }
}