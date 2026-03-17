package io.madrona.njord.js

@JsModule("maplibre-gl")
@JsName("maplibre")
@JsNonModule
external class MapLibre {
    class Map(args: dynamic) {
        fun on(type: dynamic, listener: (dynamic) -> dynamic)
        fun queryRenderedFeatures(box: Array<Array<Int>>): dynamic
        fun setStyle(url: String)
        fun remove()
        fun jumpTo(options: dynamic)
        fun fitBounds(bounds: Array<Array<Double>>)
        fun addLayer(options: dynamic)
        fun addSource(id: String, source: dynamic)
        fun addImage(id: String, image: dynamic)
        fun removeImage(id: String)
        fun listImages(): Array<String>
        fun project(lngLat: Array<Double>): dynamic
        fun addControl(control: dynamic, position: String = definedExternally)
        fun removeControl(control: dynamic)
    }

    class NavigationControl(options: dynamic = definedExternally)
    class ScaleControl(options: dynamic = definedExternally)
}
