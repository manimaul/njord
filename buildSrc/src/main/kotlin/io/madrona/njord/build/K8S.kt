package io.madrona.njord.build

import java.io.File
import java.util.*

object K8S {

    fun chartServerDeploymentWrite(
        root: File,
        version: String,
        adminKey: String,
        userName: String,
        password: String
    ): File {
        val yaml = chartServerDeployment(root, version, adminKey, userName, password)
        return File(root, "k8s_deploy/.chart_server.yaml").apply {
            writeText(yaml)
        }
    }

    fun basicAuth(userName: String, password: String): String {
        return Base64.getEncoder().encodeToString("$userName:$password".encodeToByteArray())
    }

    fun chartServerDeployment(
        root: File,
        version: String,
        adminKey: String,
        userName: String,
        password: String
    ): String {
        return File(root, "k8s_deploy/chart_server.yaml")
            .inputStream()
            .readBytes()
            .toString(Charsets.UTF_8)
            .replace(
                "ghcr.io/manimaul/njord-chart-server:latest",
                "ghcr.io/manimaul/njord-chart-server:$version"
            ).replace(
                "adminKey = {ADMIN_KEY}",
                "adminKey = \"$adminKey\""
            ).replace(
                "{BASIC_AUTH}",
                basicAuth(userName, password)
            )
    }
}