package io.madrona.njord.viewmodel

import io.madrona.njord.model.Bounds
import io.madrona.njord.model.Depth
import io.madrona.njord.model.MapGeoJsonFeature
import io.madrona.njord.model.Theme

expect class ChartController() {
    var onMoveEnd: ((MapLocation) -> Unit)?
    var onClick: ((MapPoint) -> Unit)?
    fun move(location: MapLocation)
    fun fitBounds(bounds: Bounds)
    fun queryRenderedFeatures(topLeft: MapPoint, bottomRight: MapPoint): List<MapGeoJsonFeature>
    fun setStyle(theme: Theme, depth: Depth)
}
