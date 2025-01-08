package io.madrona.njord.viewmodel

import io.madrona.njord.js.*
import io.madrona.njord.model.*
import io.madrona.njord.util.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.HTMLDivElement

actual class ChartController actual constructor() {
    var mapView: MapLibre.Map? = null
    actual var onMoveEnd: ((MapLocation) -> Unit)? = null
    actual var onClick: ((MapPoint) -> Unit)? = null

    actual fun move(location: MapLocation) {
        val options = js("{}")
        options.center = arrayOf(location.longitude, location.latitude)
        options.zoom = location.zoom
        mapView?.jumpTo(options)
    }

    actual fun fitBounds(bounds: Bounds) {
        val topLeft = arrayOf(bounds.leftLng, bounds.topLat)
        val botRight = arrayOf(bounds.rightLng, bounds.bottomLat)
        mapView?.fitBounds(arrayOf(topLeft, botRight))
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint, bottomRight: MapPoint
    ): List<MapGeoJsonFeature> {
        return mapView?.let { mapView ->
            val top = topLeft.x
            val bottom = bottomRight.x
            val right = bottomRight.y
            val left = topLeft.y
            val box = arrayOf(
                arrayOf(top, right),
                arrayOf(bottom, left)
            )
            val f: String = JSON.stringify(mapView.queryRenderedFeatures(box))
            val geoList = kotlinx.serialization.json.Json.parseToJsonElement(f)
            (geoList as? JsonArray)?.let {
                it.mapNotNull {
                    try {
                        json.decodeFromJsonElement(MapGeoJsonFeature.serializer(), it)
                    } catch (e: Exception) {
                        MapGeoJsonFeature(
                            sourceLayer = "Error",
                            properties = JsonObject(
                                mapOf(
                                    "ERROR" to JsonPrimitive("${e.message}")
                                )
                            )
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
        val style = stylePath(theme, depth)
        mapView?.setStyle(style)
    }

    fun createMapView(container: HTMLDivElement) {
        mapView = MapLibre.Map(mapLibreArgs(container)).also { mv ->
            println("creating mapview")
            chartViewModel.flow.value.bounds?.let { bounds ->
                println("setting bounds $bounds")
                val topLeft = arrayOf(bounds.leftLng, bounds.topLat)
                val botRight = arrayOf(bounds.rightLng, bounds.bottomLat)
                mv.fitBounds(arrayOf(topLeft, botRight))
                chartViewModel.setBounds(null)
            }
            mv.on("moveend") { event ->
                val center = event.target.getCenter()
                val zoom = event.target.getZoom() as Double
                val lat: Double = center.lat as Double
                val lng: Double = center.lng as Double
                onMoveEnd?.invoke(MapLocation(lng, lat, zoom))
            }
            mv.on("click") { event ->
                val x: Int = event.point.x as Int
                val y: Int = event.point.y as Int
                onClick?.invoke(MapPoint(x, y))
            }
        }
    }

    fun disposeMapView() {
        mapView?.remove()
        mapView = null
    }

    private fun mapLibreArgs(
        container: HTMLDivElement,
    ): dynamic {
        val state = chartViewModel.flow.value
        val obj = js("{}")
        obj["container"] = container
        obj["style"] = stylePath(state.theme, state.depth)
        obj["center"] = arrayOf(state.location.longitude, state.location.latitude)
        obj["zoom"] = state.location.zoom
        obj["attributionControl"] = false
        return obj
    }
}
