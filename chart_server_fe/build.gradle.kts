task<Exec>("build") {
    environment("REACT_APP_VERSION", "${project.version}")
    commandLine("bash", "-c", "npm run build")
}
