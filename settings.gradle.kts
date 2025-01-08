
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "njord"

include("chart_server")
include("geojson")
include("shared")
include("shared_fe")
include("web")
