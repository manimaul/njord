package io.madrona.njord.js

import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.stylePath
import io.madrona.njord.util.json
import io.madrona.njord.viewmodel.ChartState
import io.madrona.njord.viewmodel.MapLocation
import io.madrona.njord.viewmodel.MapPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.HTMLDivElement


@JsModule("maplibre-gl")
@JsName("maplibre")
@JsNonModule
external class MapLibre {
    class Map(args: dynamic) {
        fun on(type: dynamic, listener: (dynamic) -> dynamic)
        fun queryRenderedFeatures(box: Array<Array<Int>>): dynamic
        fun setStyle(url: String)
        fun remove()
        fun jumpTo(options: dynamic)
    }
}

fun MapLibre.Map.jumpToLocation(location: MapLocation) {
    val options = js("{}")
    options.center = arrayOf(location.longitude, location.latitude)
    options.zoom = location.zoom
    jumpTo(options)
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

fun MapLibre.Map.renderedFeatures(topLeft: MapPoint, bottomRight: MapPoint): List<MapGeoJsonFeature> {
    val top = topLeft.x
    val bottom = bottomRight.x
    val right = bottomRight.y
    val left = topLeft.y
    val box = arrayOf(
        arrayOf(top, right),
        arrayOf(bottom, left)
    )
    val f: String = JSON.stringify(queryRenderedFeatures(box))
    val geoList = Json.parseToJsonElement(f)
    return (geoList as? JsonArray)?.let {
        it.mapNotNull {
            try {
                json.decodeFromJsonElement(MapGeoJsonFeature.serializer(), it)
            } catch (e: Exception) {
                MapGeoJsonFeature(
                    sourceLayer = "Error",
                    properties = JsonObject(mapOf(
                        "ERROR" to JsonPrimitive("${e.message}")
                    ))
                )
            }
        }
    } ?: emptyList()
}

fun MapLibre.Map.onClick(callback: (MapPoint) -> Unit) {
    on("click") { event ->
        val x: Int = event.point.x as Int
        val y: Int = event.point.y as Int
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

