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

val MapLibre = fc<AppProps> { props ->
    require("maplibre-gl/dist/maplibre-gl.css")
    val mapContainer: RefObject<Any> = useRef(null)
    val map: MutableRefObject<Map> = useRef()
    val lng: Float? by useState(props.lng)
    val lat: Float? by useState(props.lat)
    val style: String? by useState(props.style)
    val zoom: Float? by useState(props.zoom)

    useEffect {
        if (map.current == null) {
            println("creating map with style $style")
            map.current = Map(
                options = json(
                    "container" to mapContainer.current,
                    "style" to style,
                    "center" to arrayOf(lng, lat),
                    "zoom" to zoom
                )
            )
        }
    }

    styledDiv {
        css {
            height = 100.pct
            width = 100.pct
        }
        ref = mapContainer
    }
}

/**
 * https://maplibre.org/maplibre-gl-js-docs/api/map/
 */
fun RBuilder.mapLibre(
    style: String = "/v1/style/meters",
    lng: Float = -122.44f,
    lat: Float = 47.257f,
    zoom: Float = 11f,
){
    MapLibre {
        attrs.also {
            it.style = style
            it.lng = lng
            it.lat = lat
            it.zoom = zoom
        }
    }
}
