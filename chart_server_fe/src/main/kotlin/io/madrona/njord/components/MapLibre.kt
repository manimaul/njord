package io.madrona.njord.components

import io.madrona.njord.Map
import kotlinext.js.require
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.css.width
import react.*
import styled.css
import styled.styledDiv
import kotlin.js.json

external interface AppState : State

external interface AppProps : Props {
    var style: String
    var lng: Float
    var lat: Float
    var zoom: Float
}

class MapLibre : RComponent<AppProps, AppState>() {

    private val mapContainer : RefObject<Any> = createRef()
    private var map: Map? = null

    init {
        require("maplibre-gl/dist/maplibre-gl.css")
    }

    override fun componentDidMount() {
        map = Map(
            options = json(
                "container" to mapContainer.current,
                "style" to props.style,
                "center" to arrayOf(props.lng, props.lat),
                "zoom" to props.zoom
            )
        )
    }

    override fun RBuilder.render() {
        styledDiv {
            css {
                height = 100.pct
                width = 100.pct
            }
            ref = mapContainer
        }
    }
}

/**
 * https://maplibre.org/maplibre-gl-js-docs/api/map/
 */
fun RBuilder.mapLibre(
    style: String = "/v1/style/day/meters",
    lng: Float = -122.44f,
    lat: Float = 47.257f,
    zoom: Float = 11f,
) = child(MapLibre::class) {
    attrs.style = style
    attrs.lng = lng
    attrs.lat = lat
    attrs.zoom = zoom
}
