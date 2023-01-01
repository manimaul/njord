package io.madrona.njord.components

import io.madrona.njord.pathToFullUrl
import react.*
import react.dom.*

val getEndpoints = listOf(
    "/v1/content/sprites/simplified.png",
    "/v1/about/version",
    "/v1/about/s57objects",
    "/v1/about/s57attributes",
    "/v1/tile_json",
    "/v1/style/meters",
    "/v1/chart?id=1",
    "/v1/chart_catalog'",
    "/v1/geojson?chart_id=17&layer_name=BOYSPP",
    "/v1/tile/0/0/0",
    "/v1/icon/.png",
    "/v1/content/fonts/Roboto Bold/0-255.pbf",
    "/v1/content/sprites/simplified.json",
    "/v1/content/sprites/simplified.png",
//    "/v1/content/upload.html",
)

val ControlEndpoints = fc<Props> {
    div {
        h2 {
            +"GET Endpoints"
        }
        ol {
            getEndpoints.forEach {
                li {
                    a(href = it.pathToFullUrl(), target = "_blank") {
                        +it
                    }
                }
            }
        }
    }
}
