package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject

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
    ): GeoJsonObject? {
        return null
    }
}