
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "njord"

include("chart_server")
include("chart_server_fe")
include("geojson")
include("shared")
include("shared_fe")
include("web")
