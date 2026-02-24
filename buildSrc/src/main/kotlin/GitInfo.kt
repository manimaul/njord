object GitInfo {

    fun gitBranch(): String {
        return CommandLine.exec("git symbolic-ref --short -q HEAD")
    }

    fun gitShortHash(): String {
        return CommandLine.exec("git rev-parse --verify --short HEAD")
    }

    fun gitUntracked() : Boolean {
        return CommandLine.exec("git diff-index --quiet HEAD -- || echo 'untracked'") == "untracked"
    }
}