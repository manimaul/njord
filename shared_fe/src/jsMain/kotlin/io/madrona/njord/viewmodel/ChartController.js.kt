package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.BoundingBox
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.js.*
import io.madrona.njord.model.*
import io.madrona.njord.util.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToDynamic
import org.w3c.dom.HTMLDivElement

@OptIn(ExperimentalSerializationApi::class)
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

    actual fun fitBounds(bounds: BoundingBox) {
        val topLeft = arrayOf(bounds.west, bounds.north)
        val botRight = arrayOf(bounds.east, bounds.south)
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
            chartViewModel.flow.value.highlight?.let { geo ->
                mv.on("load") { event ->
                    highlight(geo)
                    mv.addLayer(json.encodeToDynamic(highlightLine))
                    mv.addLayer(json.encodeToDynamic(highlightPoint))
                }
            }
            chartViewModel.flow.value.bounds?.let { bounds ->
                val topLeft = arrayOf(bounds.west, bounds.north)
                val botRight = arrayOf(bounds.east, bounds.south)
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

    @OptIn(ExperimentalSerializationApi::class)
    actual fun highlight(feature: Feature) {
        mapView?.let {
            val source = Source(
                type = SourceType.GEOJSON,
                data = feature,
            )
            val f = json.encodeToDynamic(source)
            println("highlighting feature: $f")
            it.addSource("highlight", f)
        }
    }

    actual fun project(mapLocation: MapLocation): MapPoint? {
        return mapView?.let { mapView ->
            val p = mapView.project(arrayOf(mapLocation.longitude, mapLocation.latitude))
            MapPoint(p.x, p.y)
        }
    }
}

val highlightLine = Layer(
    id = "highlight_line",
    type = LayerType.LINE,
    source = "highlight",
    layout = Layout(
        lineJoin = LineJoin.ROUND,
        lineCap = LineCap.ROUND
    ),
    paint = Paint(
        lineColor = JsonPrimitive("#D63F24"),
        lineWidth = 8.0f
    )
)

val highlightPoint = Layer(
    id = "highlight_point",
    type = LayerType.CIRCLE,
    source = "highlight",
    filter = JsonArray(listOf(JsonPrimitive("=="), JsonPrimitive("\$type"), JsonPrimitive("Point"))),
    paint = Paint(
        circleStrokeColor = JsonPrimitive("#D63F24"),
        circleOpacity = 0f,
        circleRadius = 80f,
        circleStrokeWidth = 8.0f,
    )
)
