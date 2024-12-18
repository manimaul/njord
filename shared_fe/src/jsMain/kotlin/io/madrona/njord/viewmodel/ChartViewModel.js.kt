package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.model.*
import io.madrona.njord.ui.MapLibre
import io.madrona.njord.ui.moveEnd
import io.madrona.njord.ui.onClick
import io.madrona.njord.ui.renderedFeatures

actual class ChartViewController actual constructor() {
    var mapView: MapLibre.Map? = null
        set(value) {
            field = value
            value?.moveEnd { onMoveEnd?.invoke(it) }
            value?.onClick { onClick?.invoke(it) }
        }

    actual var onMoveEnd: ((MapLocation) -> Unit)? = null
    actual var onClick: ((MapPoint) -> Unit)? = null

    actual fun move(location: MapLocation) {
    }

    actual fun fitBounds(bounds: MapLocation) {
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint, bottomRight: MapPoint
    ): GeoJsonObject? {
        return mapView?.renderedFeatures(topLeft, bottomRight)
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
        val style = stylePath(theme, depth)
        println("setting style $style")
        mapView?.setStyle(style)
    }
}
