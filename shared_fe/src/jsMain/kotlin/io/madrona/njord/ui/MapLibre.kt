package io.madrona.njord.ui

import io.madrona.njord.viewmodel.MapLocation
import org.w3c.dom.HTMLDivElement

@JsModule("maplibre-gl")
@JsName("maplibre")
@JsNonModule
external class MapLibre {
    class Map(args: dynamic) {
        fun on(type: dynamic, listener: (dynamic) -> dynamic)
    }
}

fun MapLibre.Map.moveEnd(callback: (MapLocation) -> Unit) {
    on("moveend") { event ->
        val center = event.target.getCenter()
        val zoom = event.target.getZoom() as Double
        val lat: Double = center.lat as Double
        val lng: Double = center.lng as Double
        callback(MapLocation(lng, lat, zoom))
    }
}

fun mapLibreArgs(
    container: HTMLDivElement,
    location: MapLocation
): dynamic {
    val obj = js("{}")
    obj["container"] = container
    obj["style"] = "/v1/style/feet/day"
    obj["center"] = arrayOf(location.longitude, location.latitude)
    obj["zoom"] = location.zoom
    obj["attributionControl"] = false
    return obj
}

