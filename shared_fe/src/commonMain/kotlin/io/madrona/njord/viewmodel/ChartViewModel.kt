package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.GeoJsonObject
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Theme
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class ChartState(
    val location: MapLocation = localStoreGet<MapLocation>() ?: MapLocation(),
    val theme: Theme = localStoreGet<Theme>() ?: ThemeMode.Day,
    val depth: Depth = localStoreGet<Depth>() ?: Depth.FEET,
    val query: GeoJsonObject? = null,
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
    fun setStyle(theme: Theme, depth: Depth)
}

val chartViewModel = ChartViewModel()

class ChartViewModel : BaseViewModel<ChartState>(ChartState()) {
    var controller: ChartViewController = ChartViewController()

    init {
        controller.onMoveEnd = { location ->
            println("moveend location: $location")
            setState { copy(location = location) }
        }
        controller.onClick = { point ->
            println("clicked point: $point")
            launch {
                setState {
                    copy(
                        query = controller.queryRenderedFeatures(
                            MapPoint(point.x - 5, point.y - 5),
                            MapPoint(point.x + 5, point.y + 5)
                        )
                    )
                }
            }
        }
        launch {
            flow.map { it.location }.collect {
                println("storing location: $it")
                localStoreSet(it)
            }
            flow.map { it.query }.collect {
                println("query set to : $it")
            }
        }
    }

    override fun reload() {
        setState { ChartState() }
    }

    fun setDepth(depth: Depth) {
        localStoreSet(depth)
        setState {
            controller.setStyle(this.theme, depth)
            copy(depth = depth)
        }
    }
}