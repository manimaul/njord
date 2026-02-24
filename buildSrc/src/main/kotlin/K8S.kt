import java.io.File

object K8S {

    fun chartServerDeploymentWrite(
        root: File,
        version: String,
    ): File {
        val yaml = chartServerDeployment(root, version)
        return File(root, "k8s_deploy/.chart_server.yaml").apply {
            writeText(yaml)
        }
    }

    fun chartServerDeployment(
        root: File,
        version: String,
    ): String {
        return File(root, "k8s_deploy/chart_server.yaml")
            .inputStream()
            .readBytes()
            .toString(Charsets.UTF_8)
            .replace(
                "ghcr.io/manimaul/njord-chart-server:latest",
                "ghcr.io/manimaul/njord-chart-server:$version"
            )
    }
}