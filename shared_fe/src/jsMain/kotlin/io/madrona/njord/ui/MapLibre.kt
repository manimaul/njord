package io.madrona.njord.ui

import org.w3c.dom.HTMLDivElement

@JsModule("maplibre-gl")
@JsName("maplibre")
@JsNonModule
external class MapLibre {
    class Map(args: dynamic)
}


fun mapLibreArgs(
    container: HTMLDivElement,
): dynamic {
    val obj = js("{}")
    obj["container"] = container
    obj["style"] = "/v1/style/feet/day"
    obj["center"] = js("[122.4002,47.27984]")
    obj["zoom"] = js("11.0")
    return obj
}
