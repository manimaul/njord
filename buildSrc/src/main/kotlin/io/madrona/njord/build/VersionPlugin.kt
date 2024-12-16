package io.madrona.njord.build

import io.madrona.njord.build.GitInfo.gitBranch
import io.madrona.njord.build.GitInfo.gitShortHash
import io.madrona.njord.build.GitInfo.gitUntracked
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
//        (target.properties["sourceSets"] as? SourceSetContainer)?.getByName("main")?.java?.srcDir(genBuildDir)
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

const val gitHash = "${project.gitShortHash()}"
const val gitBranch = "${project.gitBranch()}"
const val version = "${project.version}"
const val dev = "${project.gitUntracked()}"
const val buildDate = "${fmt.format(Date())}"
"""
    }
}


