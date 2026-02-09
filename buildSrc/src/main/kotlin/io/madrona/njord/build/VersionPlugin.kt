import io.madrona.njord.build.GitInfo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

interface VersionPluginExtension {
    val versionOutDir: DirectoryProperty
}

class VersionPlugin : Plugin<Project> {
    val fmt = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")

    override fun apply(target: Project) {
        val extension = target.extensions.create("versionConfig", VersionPluginExtension::class.java)
        extension.versionOutDir.convention(target.layout.projectDirectory.dir("src/commonMain/kotlin"))
        target.afterEvaluate {
            val genBuildDir = extension.versionOutDir.get().asFile
            genBuildDir.mkdirs()
            val versionFile = File(genBuildDir, "VersionInfo.kt")
            versionFile.writeText(versionSource(target))
        }
    }

    private fun versionSource(project: Project) : String {
        return """
object VersionInfo {
    const val gitHash = "${GitInfo.gitShortHash()}"
    const val gitBranch = "${GitInfo.gitBranch()}"
    const val version = "${project.version}"
    const val dev = "${GitInfo.gitUntracked()}"
    const val buildDate = "${fmt.format(Date())}"
}
"""
    }
}


