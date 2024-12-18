package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class ChartState(
    val location: MapLocation = localStoreGet<MapLocation>() ?: MapLocation()
) : VmState

@Serializable
data class MapLocation(
    val longitude: Double = -118.512,
    val latitude: Double = 33.96832,
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
        launch {
            flow.map { it.location }.collect {
                println("storing location: $it")
                localStoreSet(it)
            }
        }
    }

    override fun reload() {
        setState { ChartState() }
    }
}