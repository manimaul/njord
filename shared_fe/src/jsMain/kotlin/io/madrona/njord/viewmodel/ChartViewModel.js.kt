package io.madrona.njord.viewmodel

import io.madrona.njord.js.*
import io.madrona.njord.model.Depth
import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.Theme
import io.madrona.njord.model.stylePath

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
        mapView?.jumpToLocation(location)
    }

    actual fun fitBounds(bounds: MapLocation) {
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint, bottomRight: MapPoint
    ): List<MapGeoJsonFeature> {
        return mapView?.renderedFeatures(topLeft, bottomRight) ?: emptyList()
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
        val style = stylePath(theme, depth)
        mapView?.setStyle(style)
    }
}
