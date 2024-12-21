package io.madrona.njord.viewmodel

import io.madrona.njord.model.Depth
import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.Theme

actual class ChartViewController actual constructor() {
    actual var onMoveEnd: ((MapLocation) -> Unit)? = null
    actual var onClick: ((MapPoint) -> Unit)? = null

    actual fun move(location: MapLocation) {
    }

    actual fun fitBounds(bounds: MapLocation) {
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint,
        bottomRight: MapPoint
    ): List<MapGeoJsonFeature> {
        return emptyList()
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
    }
}