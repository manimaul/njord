
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "njord"

include("server")
include("shared")
include("shared_fe")
include("web")
include("libpq")
include("libgdal")
include("geojson")
