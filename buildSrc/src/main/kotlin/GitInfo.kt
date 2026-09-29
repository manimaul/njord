/**
 * Git metadata baked into VersionInfo.kt.
 *
 * Each value can be supplied via environment variable instead of running git. The container
 * build needs this: njord is usually checked out as a submodule, where `.git` is a file pointing
 * outside the podman build context, so git cannot run inside the builder stage. `makeImg`
 * resolves these on the host and passes them in as build args.
 */
object GitInfo {

    fun gitBranch(): String {
        return env("GIT_BRANCH") ?: CommandLine.exec("git symbolic-ref --short -q HEAD")
    }

    fun gitShortHash(): String {
        return env("GIT_HASH") ?: CommandLine.exec("git rev-parse --verify --short HEAD")
    }

    fun gitUntracked() : Boolean {
        env("GIT_DIRTY")?.let { return it == "true" }
        return CommandLine.exec("git diff-index --quiet HEAD -- || echo 'untracked'") == "untracked"
    }

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
}
