package io.madrona.njord.ui

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Theme
import io.madrona.njord.model.stylePath
import io.madrona.njord.viewmodel.ChartState
import io.madrona.njord.viewmodel.MapLocation
import io.madrona.njord.viewmodel.MapPoint
import org.w3c.dom.HTMLDivElement

@JsModule("maplibre-gl")
@JsName("maplibre")
@JsNonModule
external class MapLibre {
    class Map(args: dynamic) {
        fun on(type: dynamic, listener: (dynamic) -> dynamic)
        fun queryRenderedFeatures(box: Array<DoubleArray>)
        fun setStyle(url: String)
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

fun MapLibre.Map.renderedFeatures(topLeft: MapPoint, bottomRight: MapPoint) : GeoJsonObject? {
    return null
}

fun MapLibre.Map.onClick(callback: (MapPoint) -> Unit) {
    on("click") { event ->
        val x: Double = event.point.x as Double
        val y: Double = event.point.y as Double
        callback(MapPoint(x, y))
    }
}

fun mapLibreArgs(
    container: HTMLDivElement,
    state: ChartState,
): dynamic {
    val obj = js("{}")
    obj["container"] = container
    obj["style"] = stylePath(state.theme, state.depth)
    obj["center"] = arrayOf(state.location.longitude, state.location.latitude)
    obj["zoom"] = state.location.zoom
    obj["attributionControl"] = false
    return obj
}

