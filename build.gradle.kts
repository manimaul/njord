
task("version") {
    doLast {
        println("version: ${project.version}")
        println("group: ${project.group}")
    }
}
