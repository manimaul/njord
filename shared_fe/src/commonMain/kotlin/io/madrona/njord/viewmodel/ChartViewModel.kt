package io.madrona.njord.viewmodel

import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.Point
import io.madrona.njord.model.*
import io.madrona.njord.routing.QueryParams
import io.madrona.njord.routing.Route
import io.madrona.njord.routing.Routing
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private fun mapLocation(): MapLocation? {
    return routeViewModel.flow.value.current.takeIf {
        it.route == Route.Enc
    }?.let { it.params?.values }?.let {
        val lng = it["lng"]?.toDoubleOrNull()
        val lat = it["lat"]?.toDoubleOrNull()
        val z = it["z"]?.toDoubleOrNull()
        if (lng != null && lat != null && z != null) {
            MapLocation(lng, lat, z)
        } else {
            null
        }
    } ?: localStoreGet<MapLocation>()
}

data class ChartState(
    val location: MapLocation = mapLocation() ?: MapLocation(),
    val bounds: Bounds? = null,
    val highlight: Feature? = null,
    val theme: Theme = localStoreGet<Theme>() ?: ThemeMode.Day,
    val depth: Depth = localStoreGet<Depth>() ?: Depth.FEET,
    val query: List<MapGeoJsonFeature> = emptyList(),
)

@Serializable
data class MapLocation(
    val longitude: Double = -118.512,
    val latitude: Double = 33.96832,
    val zoom: Double = 12.0,
)

data class MapPoint(
    val x: Int,
    val y: Int,
)


val chartViewModel = ChartViewModel()

fun currentThemeMode() = chartViewModel.flow.value.theme.mode()

class ChartViewModel : BaseViewModel<ChartState>(ChartState()) {
    var controller: ChartController = ChartController()

    init {
        controller.onMoveEnd = { location ->
            println("moveend location: $location")
            setState { copy(location = location) }
        }
        controller.onClick = { point ->
            println("click point: $point")
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
                if (routeViewModel.flow.value.current.route == Route.Enc) {
                    routeViewModel.replaceRoute(
                        Routing.from(
                            "/enc",
                            QueryParams("lat=${it.latitude}&lng=${it.longitude}&z=${it.zoom}")
                        )
                    )
                }
                localStoreSet(it)
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

    fun setLocation(mapLocation: MapLocation, highLightLocation: Boolean = false) {
        setState {
            controller.move(mapLocation)
            val highlight = if (highLightLocation) {
                Feature(geometry = Point(mapLocation.longitude, mapLocation.latitude))
            } else {
                null
            }
            highlight?.let { controller.highlight(it) }
            copy(location = mapLocation, highlight = highlight)
        }
    }

    fun setBounds(bounds: Bounds?, highlight: Feature? = null) {
        setState {
            bounds?.let { controller.fitBounds(it) }
            highlight?.let { controller.highlight(it) }
            copy(bounds = bounds, highlight = highlight)
        }
    }
}