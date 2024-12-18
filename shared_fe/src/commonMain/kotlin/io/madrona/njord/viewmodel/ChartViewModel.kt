package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject

data class ChartState(
    val location: MapLocation = MapLocation()
) : VmState

data class MapLocation(
    val latitude: Double = 33.96832,
    val longitude: Double = -118.512,
    val zoom: Double = 12.0,
)

data class MapBounds(
    val leftLng: Double,
    val topLat: Double,
    val rightLng: Double,
    val bottomLat: Double,
)

data class MapPoint(
    val x: Double,
    val y: Double,
)


expect class ChartViewController() {
    var onMoveEnd: ((MapLocation) -> Unit)?
    var onClick: ((MapPoint) -> Unit)?
    fun move(location: MapLocation)
    fun fitBounds(bounds: MapLocation)
    fun queryRenderedFeatures(topLeft: MapPoint, bottomRight: MapPoint): GeoJsonObject?
}

class ChartViewModel : BaseViewModel<ChartState>(ChartState()) {
    var controller: ChartViewController = ChartViewController()

    init {
        controller.onMoveEnd = { location ->
            println("moveend location: $location")
            setState { copy(location = location) }
        }
        controller.onClick = { location ->
           println("clicked location: $location")
        }
    }

    override fun reload() {
        setState { ChartState() }
    }
}