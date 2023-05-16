package io.madrona.njord.build

import org.gradle.api.Project
import java.io.ByteArrayOutputStream

object GitInfo {

    fun Project.gitBranch(): String {
        return commandLine("git symbolic-ref --short -q HEAD")
    }

    fun Project.gitShortHash(): String {
        return commandLine("git rev-parse --verify --short HEAD")
    }

    fun Project.gitUntracked() : Boolean {
        return commandLine("git diff-index --quiet HEAD -- || echo 'untracked'") == "untracked"
    }

    private fun Project.commandLine(cmd: String) : String {
        val stdout = ByteArrayOutputStream()
        rootProject.exec {
            it.commandLine("bash", "-c", cmd)
            it.standardOutput = stdout
        }
        return stdout.toString().trim()

    }
}