package io.madrona.njord.viewmodel

import io.madrona.njord.model.*
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class ChartState(
    val location: MapLocation = localStoreGet<MapLocation>() ?: MapLocation(),
    val theme: Theme = localStoreGet<Theme>() ?: ThemeMode.Day,
    val depth: Depth = localStoreGet<Depth>() ?: Depth.FEET,
    val query: List<MapGeoJsonFeature> = emptyList(),
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
    val x: Int,
    val y: Int,
)

expect class ChartViewController() {
    var onMoveEnd: ((MapLocation) -> Unit)?
    var onClick: ((MapPoint) -> Unit)?
    fun move(location: MapLocation)
    fun fitBounds(bounds: MapLocation)
    fun queryRenderedFeatures(topLeft: MapPoint, bottomRight: MapPoint): List<MapGeoJsonFeature>
    fun setStyle(theme: Theme, depth: Depth)
}

val chartViewModel = ChartViewModel()

fun currentThemeMode() = chartViewModel.flow.value.theme.mode()

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
            controller.setStyle(theme, depth)
            copy(depth = depth)
        }
    }

    fun setCustomColor(color: String) {
        withState {
            val newTheme: Theme = if (color == "Default") {
                it.theme.mode()
            } else {
                CustomTheme(it.theme.mode(), color)
            }
            setTheme(newTheme)
        }
    }

    fun setTheme(theme: Theme) {
        localStoreSet(theme)
        setState {
            controller.setStyle(theme, depth)
            copy(theme = theme)
        }
    }

    fun clearQuery() {
        setState { copy(query = emptyList()) }
    }
}