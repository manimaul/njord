package io.madrona.njord.build

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val taskName = "makeVersionFile"

class VersionPlugin : Plugin<Project> {
    val fmt = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")

    override fun apply(target: Project) {
        val genBuildDir = File(target.buildDir, "generated/source/version")
        target.task(taskName) {
            it.mustRunAfter("clean")
            genBuildDir.mkdirs()
            val versionFile = File(genBuildDir, "VersionInfo.kt")
            versionFile.writeText(versionSource(target))
        }
        target.tasks.getByName("compileJava") {
            it.dependsOn(taskName)
        }
    }

    private fun versionSource(project: Project) : String {
        return """package io.madrona.njord.util

const val gitHash = "${GitInfo.gitShortHash()}"
const val gitBranch = "${GitInfo.gitBranch()}"
const val version = "${project.version}"
const val dev = "${GitInfo.gitUntracked()}"
const val buildDate = "${fmt.format(Date())}"
"""
    }
}


