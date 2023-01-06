task<Exec>("build") {
    environment("REACT_APP_VERSION", "${project.version}")
    commandLine("npm", "run", "build")
}
