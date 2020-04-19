plugins {
    application
}

application {
    mainClassName = "io.madrona.njord.AppKt"
    applicationName = "njord-client"
}

dependencies {
    //https://github.com/tbsalling/aismessages
    // todo (WK) implementation("dk.tbsalling:aismessages:2.2.2")

    /* Project */
    implementation(project(":common"))
}
