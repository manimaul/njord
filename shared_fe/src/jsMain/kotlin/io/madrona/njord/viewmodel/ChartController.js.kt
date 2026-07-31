package io.madrona.njord.viewmodel

import io.madrona.njord.buildSectorSvg
import io.madrona.njord.geojson.BoundingBox
import io.madrona.njord.geojson.Feature
import io.madrona.njord.geojson.FeatureCollection
import io.madrona.njord.geojson.Geometry
import io.madrona.njord.geojson.MultiPolygon
import io.madrona.njord.geojson.Point
import io.madrona.njord.geojson.Polygon
import io.madrona.njord.geojson.Position
import io.madrona.njord.js.*
import io.madrona.njord.model.*
import io.madrona.njord.network.Network
import io.madrona.njord.util.json
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToDynamic
import kotlinx.serialization.json.put
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.Node
import org.w3c.dom.events.EventListener

private const val REGION_MIN_ZOOM = 0
private const val REGION_MAX_ZOOM_EXCLUSIVE = 7

private const val MOVE_ICON_SVG = """<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="5 9 2 12 5 15"></polyline><polyline points="9 5 12 2 15 5"></polyline><polyline points="15 19 12 22 9 19"></polyline><polyline points="19 9 22 12 19 15"></polyline><line x1="2" y1="12" x2="22" y2="12"></line><line x1="12" y1="2" x2="12" y2="22"></line></svg>"""

private const val DELETE_ICON_SVG = """<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>"""

private data class RectPx(val left: Int, val top: Int, val right: Int, val bottom: Int)

@OptIn(ExperimentalSerializationApi::class)
actual class ChartController actual constructor() {
    var mapView: MapLibre.Map? = null
    var themeMode: ThemeMode? = null
    var scaleControl: MapLibre.ScaleControl? = null
    actual var onMoveEnd: ((MapLocation) -> Unit)? = null
    actual var onClick: ((MapPoint) -> Unit)? = null

    actual fun move(location: MapLocation) {
        val options = js("{}")
        options.center = arrayOf(location.longitude, location.latitude)
        options.zoom = location.zoom
        mapView?.jumpTo(options)
    }

    actual fun fitBounds(bounds: BoundingBox) {
        val topLeft = arrayOf(bounds.west, bounds.north)
        val botRight = arrayOf(bounds.east, bounds.south)
        mapView?.fitBounds(arrayOf(topLeft, botRight))
    }

    actual fun queryRenderedFeatures(
        topLeft: MapPoint, bottomRight: MapPoint
    ): List<MapGeoJsonFeature> {
        return mapView?.let { mapView ->
            val top = topLeft.x
            val bottom = bottomRight.x
            val right = bottomRight.y
            val left = topLeft.y
            val box = arrayOf(
                arrayOf(top, right),
                arrayOf(bottom, left)
            )
            val f: String = JSON.stringify(mapView.queryRenderedFeatures(box))
            val geoList = kotlinx.serialization.json.Json.parseToJsonElement(f)
            (geoList as? JsonArray)?.let {
                it.mapNotNull {
                    try {
                        json.decodeFromJsonElement(MapGeoJsonFeature.serializer(), it)
                    } catch (e: Exception) {
                        MapGeoJsonFeature(
                            sourceLayer = "Error",
                            properties = JsonObject(
                                mapOf(
                                    "ERROR" to JsonPrimitive("${e.message}")
                                )
                            )
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    actual fun setStyle(theme: Theme, depth: Depth) {
        themeMode = theme.mode()
        val style = stylePath(theme, depth)
        mapView?.let { mv ->
            mv.listImages().filter { it.startsWith("sector_") }.forEach { mv.removeImage(it) }
            // diff:false forces a full style reload so "style.load" fires again and loadRegions()
            // re-adds the regions source/layers; MapLibre's default diff-based setStyle silently
            // drops sources/layers that aren't part of the new style JSON without re-firing "style.load".
            mv.setStyle(style, js("{ diff: false }"))
            addScaleControl(mv, depth)
        }
    }

    private fun addScaleControl(map: MapLibre.Map, depth: Depth) {
        scaleControl?.let { map.removeControl(it) }
        val scaleOpts = js("{}")
        scaleOpts["unit"] = when (depth) {
            Depth.METERS -> "metric"
            else -> "nautical"
        }
        MapLibre.ScaleControl(scaleOpts).also {
            scaleControl = it
            map.addControl(it, "bottom-left")
        }
    }

    fun createMapView(container: HTMLDivElement) {
        themeMode = chartViewModel.flow.value.theme.mode()
        mapContainer = container
        mapView = MapLibre.Map(mapLibreArgs(container)).also { mv ->
            mv.addControl(MapLibre.NavigationControl(), "top-right")
            addScaleControl(mv, chartViewModel.flow.value.depth)
            chartViewModel.flow.value.highlight?.let { geo ->
                mv.on("load") { event ->
                    highlight(geo)
                    mv.addLayer(json.encodeToDynamic(highlightLine))
                    mv.addLayer(json.encodeToDynamic(highlightPoint))
                }
            }
            chartViewModel.flow.value.bounds?.let { bounds ->
                val topLeft = arrayOf(bounds.west, bounds.north)
                val botRight = arrayOf(bounds.east, bounds.south)
                mv.fitBounds(arrayOf(topLeft, botRight))
                chartViewModel.setBounds(null)
            }
            mv.on("moveend") { event ->
                val center = event.target.getCenter()
                val zoom = event.target.getZoom() as Double
                val lat: Double = center.lat as Double
                val lng: Double = center.lng as Double
                onMoveEnd?.invoke(MapLocation(lng, lat, zoom))
            }
            mv.on("click") { event ->
                // Chart feature queries (the "Chart Query" modal) don't make sense while the region
                // overlay's own zoom range is displayed — regions are broad overviews, not individual
                // charts/features to inspect.
                val zoom = event.target.getZoom() as Double
                if (zoom >= REGION_MAX_ZOOM_EXCLUSIVE) {
                    val x: Int = event.point.x as Int
                    val y: Int = event.point.y as Int
                    onClick?.invoke(MapPoint(x, y))
                }
            }
            mv.on("click", "region_fill") { event ->
                if (adminViewModel.flow.value.isLoggedIn) {
                    val features = event.features
                    if (features != null && features.length > 0) {
                        (features[0].properties.name as? String)?.let { name ->
                            toggleRegionEdit(mv, name)
                        }
                    }
                }
            }
            // The region currently being edited is excluded from the "regions" source (so it isn't
            // double-drawn under the live edit overlay), so clicks on its area land on
            // "region_edit_fill" instead of "region_fill" — this is what lets clicking it again exit.
            mv.on("click", "region_edit_fill") { event ->
                if (adminViewModel.flow.value.isLoggedIn) {
                    exitRegionEdit(mv)
                }
            }
            mv.on("mousedown", "region_edit_points") { event ->
                // Only the left button starts a drag; leaving the right button (originalEvent.button
                // == 2) untouched is required so Chromium still synthesizes "contextmenu" afterward —
                // calling preventDefault() on a right-button mousedown suppresses that entirely.
                val button = event.originalEvent?.button as? Int
                if (adminViewModel.flow.value.isLoggedIn && !deleteMode && (button == null || button == 0)) {
                    event.preventDefault()
                    val features = event.features
                    if (features != null && features.length > 0) {
                        val props = features[0].properties
                        dragHandle = Triple(props.part as Int, props.ring as Int, props.vertex as Int)
                        mv.asDynamic().dragPan.disable()
                    }
                }
            }
            mv.on("mousedown") { event ->
                // In delete mode, a left-button drag anywhere on the map (not just on a point) starts
                // a rubber-band selection box instead of moving a point.
                val button = event.originalEvent?.button as? Int
                if (adminViewModel.flow.value.isLoggedIn && deleteMode && editRegionName != null &&
                    (button == null || button == 0)
                ) {
                    event.preventDefault()
                    val x: Int = event.point.x as Int
                    val y: Int = event.point.y as Int
                    startBoxSelect(mv, x, y)
                }
            }
            mv.on("mousemove") { event ->
                dragHandle?.let { (pi, ri, vi) ->
                    val lng = event.lngLat.lng as Double
                    val lat = event.lngLat.lat as Double
                    editParts.getOrNull(pi)?.getOrNull(ri)?.let { ring ->
                        if (vi < ring.size) ring[vi] = Position(lng, lat)
                    }
                    refreshRegionEditSources(mv)
                }
                if (boxSelectStart != null) {
                    val x: Int = event.point.x as Int
                    val y: Int = event.point.y as Int
                    updateBoxSelect(x, y)
                }
            }
            mv.on("mouseup") { event ->
                if (dragHandle != null) {
                    dragHandle = null
                    mv.asDynamic().dragPan.enable()
                }
                if (boxSelectStart != null) {
                    val x: Int = event.point.x as Int
                    val y: Int = event.point.y as Int
                    finishBoxSelect(mv, x, y)
                    mv.asDynamic().dragPan.enable()
                }
            }
            mv.on("contextmenu", "region_edit_points") { event ->
                if (adminViewModel.flow.value.isLoggedIn && !deleteMode) {
                    event.preventDefault()
                    val features = event.features
                    if (features != null && features.length > 0) {
                        val props = features[0].properties
                        val x: Int = event.point.x as Int
                        val y: Int = event.point.y as Int
                        showVertexContextMenu(
                            mv, x, y,
                            part = props.part as Int,
                            ring = props.ring as Int,
                            vertex = props.vertex as Int,
                        )
                    }
                }
            }
            mv.on("styleimagemissing") { event ->
                (event.id as? String)?.takeIf { it.startsWith("sector_") }?.let {
                    addSectorImage(mv, it)
                }
            }
            mv.on("style.load") { event ->
                loadRegions(mv)
            }
        }
    }

    private var regions: List<RegionManifestEntry> = emptyList()
    private var regionFilter: String? = null

    private fun loadRegions(map: MapLibre.Map) {
        CoroutineScope(Dispatchers.Default).launch {
            regions = Network.getRegions().body?.filter { it.name != "WORLD" } ?: emptyList()
            val source = Source(
                type = SourceType.GEOJSON,
                data = regionFeatureCollection(),
            )
            map.addSource("regions", json.encodeToDynamic(source))
            map.addLayer(json.encodeToDynamic(regionFillLayer()))
            map.addLayer(json.encodeToDynamic(regionOutlineLayer()))

            val labelSource = Source(
                type = SourceType.GEOJSON,
                data = regionLabelFeatureCollection(),
            )
            map.addSource("region_labels", json.encodeToDynamic(labelSource))
            map.addLayer(json.encodeToDynamic(regionLabelNameLayer()))
            map.addLayer(json.encodeToDynamic(regionLabelDescriptionLayer()))

            if (editRegionName != null) {
                addRegionEditLayers(map)
            }
        }
    }

    private fun regionFeatureCollection(): FeatureCollection {
        val filtered = regionFilter?.let { name -> regions.filter { it.name == name } } ?: regions
        val features = filtered.filter { it.name != editRegionName }.mapNotNull { entry ->
            (entry.coverageGeo as? Geometry)?.let { geometry ->
                Feature(geometry = geometry, properties = buildJsonObject { put("name", entry.name) })
            }
        }
        return FeatureCollection(features)
    }

    private fun regionLabelFeatureCollection(): FeatureCollection {
        val filtered = regionFilter?.let { name -> regions.filter { it.name == name } } ?: regions
        val features = filtered.mapNotNull { entry ->
            entry.labelPoint?.let { point ->
                Feature(
                    geometry = point,
                    properties = buildJsonObject {
                        put("name", entry.name)
                        put("description", entry.description)
                    },
                )
            }
        }
        return FeatureCollection(features)
    }

    private fun regionColor(): String = when (themeMode) {
        ThemeMode.Dusk, ThemeMode.Night -> "#FFFFFF"
        ThemeMode.Day, null -> "#000000"
    }

    private fun regionFillLayer() = Layer(
        id = "region_fill",
        type = LayerType.FILL,
        source = "regions",
        minZoom = REGION_MIN_ZOOM,
        maxZoomExclusive = REGION_MAX_ZOOM_EXCLUSIVE,
        paint = Paint(
            fillColor = JsonPrimitive(regionColor()),
            fillOpacity = 0.5f,
        )
    )

    private fun regionOutlineLayer() = Layer(
        id = "region_outline",
        type = LayerType.LINE,
        source = "regions",
        minZoom = REGION_MIN_ZOOM,
        maxZoomExclusive = REGION_MAX_ZOOM_EXCLUSIVE,
        paint = Paint(
            lineColor = JsonPrimitive(regionColor()),
            lineWidth = 1.0f,
        )
    )

    private fun regionLabelHaloColor(): String = when (themeMode) {
        ThemeMode.Dusk, ThemeMode.Night -> "#000000"
        ThemeMode.Day, null -> "#FFFFFF"
    }

    private fun regionLabelNameLayer() = Layer(
        id = "region_label_name",
        type = LayerType.SYMBOL,
        source = "region_labels",
        minZoom = 1,
        maxZoomExclusive = REGION_MAX_ZOOM_EXCLUSIVE,
        layout = Layout(
            textField = JsonArray(listOf(JsonPrimitive("get"), JsonPrimitive("name"))),
            textFont = listOf(Font.ROBOTO_BOLD),
            textSize = 13.0f,
            textAnchor = Anchor.BOTTOM,
            textJustify = TextJustify.CENTER,
            textOffset = JsonArray(listOf(JsonPrimitive(0.0f), JsonPrimitive(-0.5f))),
            textAllowOverlap = true,
            textIgnorePlacement = true,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = JsonPrimitive(regionColor()),
            textHaloColor = regionLabelHaloColor(),
            textHaloWidth = 1.5f,
        )
    )

    private fun regionLabelDescriptionLayer() = Layer(
        id = "region_label_description",
        type = LayerType.SYMBOL,
        source = "region_labels",
        minZoom = 2,
        maxZoomExclusive = REGION_MAX_ZOOM_EXCLUSIVE,
        layout = Layout(
            textField = JsonArray(listOf(JsonPrimitive("get"), JsonPrimitive("description"))),
            textFont = listOf(Font.ROBOTO_REGULAR),
            textSize = 11.0f,
            textAnchor = Anchor.TOP,
            textJustify = TextJustify.CENTER,
            textOffset = JsonArray(listOf(JsonPrimitive(0.0f), JsonPrimitive(0.1f))),
            textAllowOverlap = true,
            textIgnorePlacement = true,
            symbolPlacement = Placement.POINT,
        ),
        paint = Paint(
            textColor = JsonPrimitive(regionColor()),
            textHaloColor = regionLabelHaloColor(),
            textHaloWidth = 1.5f,
        )
    )

    actual fun setRegionFilter(region: String?) {
        regionFilter = region
        mapView?.getSource("regions")?.setData(json.encodeToDynamic(regionFeatureCollection()))
        mapView?.getSource("region_labels")?.setData(json.encodeToDynamic(regionLabelFeatureCollection()))
    }

    // --- Region editing (admin only): click a region's fill to reveal draggable vertex handles. ---

    private var mapContainer: HTMLDivElement? = null
    private var editRegionName: String? = null
    private var editParts: MutableList<MutableList<MutableList<Position>>> = mutableListOf()
    private var dragHandle: Triple<Int, Int, Int>? = null
    private var deleteMode: Boolean = false

    private fun toggleRegionEdit(map: MapLibre.Map, name: String) {
        if (editRegionName == name) {
            exitRegionEdit(map)
            return
        }
        if (editRegionName != null) {
            exitRegionEdit(map)
        }
        val entry = regions.firstOrNull { it.name == name } ?: return
        val geometry = entry.coverageGeo as? Geometry ?: return
        editRegionName = entry.name
        editParts = when (geometry) {
            is Polygon -> mutableListOf(geometry.coordinates.map { dedupeRing(it).toMutableList() }.toMutableList())
            is MultiPolygon -> geometry.coordinates
                .map { rings -> rings.map { dedupeRing(it).toMutableList() }.toMutableList() }
                .toMutableList()
            else -> mutableListOf()
        }
        mapView?.getSource("regions")?.setData(json.encodeToDynamic(regionFeatureCollection()))
        mapView?.getSource("region_labels")?.setData(json.encodeToDynamic(regionLabelFeatureCollection()))
        addRegionEditLayers(map)
        showRegionEditToolbar(map)
    }

    private fun exitRegionEdit(map: MapLibre.Map) {
        removeVertexContextMenu()
        clearBoxSelect()
        removeRegionEditToolbar()
        dragHandle = null
        deleteMode = false
        map.removeLayer("region_edit_points")
        map.removeSource("region_edit_points")
        map.removeLayer("region_edit_outline")
        map.removeLayer("region_edit_fill")
        map.removeSource("region_edit")
        editRegionName = null
        editParts = mutableListOf()
        mapView?.getSource("regions")?.setData(json.encodeToDynamic(regionFeatureCollection()))
        mapView?.getSource("region_labels")?.setData(json.encodeToDynamic(regionLabelFeatureCollection()))
    }

    private fun addRegionEditLayers(map: MapLibre.Map) {
        map.addSource(
            "region_edit",
            json.encodeToDynamic(Source(type = SourceType.GEOJSON, data = Feature(geometry = editGeometry())))
        )
        map.addLayer(json.encodeToDynamic(regionEditFillLayer()))
        map.addLayer(json.encodeToDynamic(regionEditOutlineLayer()))
        map.addSource(
            "region_edit_points",
            json.encodeToDynamic(Source(type = SourceType.GEOJSON, data = editPointsFeatureCollection()))
        )
        map.addLayer(json.encodeToDynamic(regionEditPointsLayer()))
    }

    private fun refreshRegionEditSources(map: MapLibre.Map) {
        map.getSource("region_edit")?.setData(json.encodeToDynamic(Feature(geometry = editGeometry())))
        map.getSource("region_edit_points")?.setData(json.encodeToDynamic(editPointsFeatureCollection()))
    }

    // --- Edit-mode toolbar: switch between "move points" (default) and "box-delete points" tools. ---

    private var regionEditToolbarElement: HTMLDivElement? = null

    // MapLibre's mouse-event `.point` and `map.project(...)` are both relative to the *map
    // container's own* top-left corner, not the browser viewport — but overlay elements here are
    // `position:fixed`/appended to `document.body`, which is positioned relative to the viewport.
    // Any DOM overlay placed using container-relative coordinates must add this offset first.
    private fun containerOffset(): Pair<Double, Double> {
        val rect = mapContainer?.getBoundingClientRect()
        return (rect?.left ?: 0.0) to (rect?.top ?: 0.0)
    }

    private fun updateRegionEditPointsStyle(map: MapLibre.Map) {
        map.setPaintProperty("region_edit_points", "circle-color", regionEditPointColor())
        map.setPaintProperty("region_edit_points", "circle-radius", regionEditPointRadius())
        map.setPaintProperty("region_edit_points", "circle-stroke-color", regionEditPointStrokeColor())
    }

    private fun showRegionEditToolbar(map: MapLibre.Map) {
        removeRegionEditToolbar()
        val top = (mapContainer?.getBoundingClientRect()?.top ?: 0.0) + 10.0

        val toolbar = document.createElement("div") as HTMLDivElement
        toolbar.id = "region-edit-toolbar"
        toolbar.style.cssText =
            "position:fixed;top:${top}px;left:50%;transform:translateX(-50%);" +
                "display:flex;gap:4px;background:#fff;border:1px solid #888;border-radius:6px;" +
                "box-shadow:0 2px 8px rgba(0,0,0,0.3);padding:4px;z-index:9000;"

        fun addToolButton(svg: String, active: Boolean, title: String, onSelect: () -> Unit) {
            val btn = document.createElement("div") as HTMLDivElement
            btn.innerHTML = svg
            btn.title = title
            btn.style.cssText =
                "width:32px;height:32px;display:flex;align-items:center;justify-content:center;" +
                    "border-radius:4px;cursor:pointer;color:#222;" +
                    if (active) "background:#cfe3ff;" else "background:transparent;"
            btn.addEventListener("click", EventListener { onSelect() })
            toolbar.appendChild(btn)
        }

        addToolButton(MOVE_ICON_SVG, active = !deleteMode, title = "Move points") {
            if (deleteMode) {
                deleteMode = false
                clearBoxSelect()
                updateRegionEditPointsStyle(map)
                showRegionEditToolbar(map)
            }
        }
        addToolButton(DELETE_ICON_SVG, active = deleteMode, title = "Box-delete points") {
            if (!deleteMode) {
                deleteMode = true
                dragHandle = null
                updateRegionEditPointsStyle(map)
                showRegionEditToolbar(map)
            }
        }

        document.body?.appendChild(toolbar)
        regionEditToolbarElement = toolbar
    }

    private fun removeRegionEditToolbar() {
        regionEditToolbarElement?.remove()
        regionEditToolbarElement = null
    }

    // --- Box-delete tool: drag a rectangle, then confirm deleting every point it contains. ---

    private var boxSelectStart: Pair<Int, Int>? = null
    private var boxSelectElement: HTMLDivElement? = null
    private var boxSelectButtonsElement: HTMLDivElement? = null
    private var boxSelectFinalRect: RectPx? = null

    private fun startBoxSelect(map: MapLibre.Map, x: Int, y: Int) {
        clearBoxSelect()
        boxSelectStart = x to y
        val box = document.createElement("div") as HTMLDivElement
        box.id = "region-edit-box-select"
        box.style.cssText =
            "position:fixed;border:2px dashed #2288ff;background:rgba(34,136,255,0.15);" +
                "z-index:8500;pointer-events:none;"
        document.body?.appendChild(box)
        boxSelectElement = box
        positionBoxElement(x, y, x, y)
    }

    private fun positionBoxElement(x1: Int, y1: Int, x2: Int, y2: Int) {
        val box = boxSelectElement ?: return
        val (offsetX, offsetY) = containerOffset()
        box.style.left = "${offsetX + minOf(x1, x2)}px"
        box.style.top = "${offsetY + minOf(y1, y2)}px"
        box.style.width = "${kotlin.math.abs(x2 - x1)}px"
        box.style.height = "${kotlin.math.abs(y2 - y1)}px"
    }

    private fun updateBoxSelect(x: Int, y: Int) {
        val (sx, sy) = boxSelectStart ?: return
        positionBoxElement(sx, sy, x, y)
    }

    private fun finishBoxSelect(map: MapLibre.Map, endX: Int, endY: Int) {
        val (sx, sy) = boxSelectStart ?: return
        boxSelectStart = null
        val rect = RectPx(
            left = minOf(sx, endX),
            top = minOf(sy, endY),
            right = maxOf(sx, endX),
            bottom = maxOf(sy, endY),
        )
        boxSelectFinalRect = rect
        showBoxSelectButtons(map, rect)
    }

    private fun showBoxSelectButtons(map: MapLibre.Map, rect: RectPx) {
        boxSelectButtonsElement?.remove()
        val (offsetX, offsetY) = containerOffset()
        val centerX = offsetX + (rect.left + rect.right) / 2.0
        val centerY = offsetY + (rect.top + rect.bottom) / 2.0

        val panel = document.createElement("div") as HTMLDivElement
        panel.id = "region-edit-box-buttons"
        panel.style.cssText =
            "position:fixed;left:${centerX}px;top:${centerY}px;transform:translate(-50%,-50%);" +
                "display:flex;gap:6px;z-index:9000;"

        fun addButton(label: String, bg: String, action: () -> Unit) {
            val btn = document.createElement("div") as HTMLDivElement
            btn.textContent = label
            btn.style.cssText =
                "padding:6px 12px;background:$bg;color:#fff;border-radius:4px;cursor:pointer;" +
                    "font-size:13px;box-shadow:0 1px 4px rgba(0,0,0,0.4);white-space:nowrap;"
            btn.addEventListener("click", EventListener { action() })
            panel.appendChild(btn)
        }

        addButton("Delete", "#c0392b") {
            deletePointsInBox(map, rect)
            clearBoxSelect()
        }
        addButton("Clear", "#555555") {
            clearBoxSelect()
        }

        document.body?.appendChild(panel)
        boxSelectButtonsElement = panel
    }

    private fun clearBoxSelect() {
        boxSelectStart = null
        boxSelectElement?.remove()
        boxSelectElement = null
        boxSelectButtonsElement?.remove()
        boxSelectButtonsElement = null
        boxSelectFinalRect = null
    }

    private fun deletePointsInBox(map: MapLibre.Map, rect: RectPx) {
        val toDelete = mutableListOf<Triple<Int, Int, Int>>()
        editParts.forEachIndexed { pi, rings ->
            rings.forEachIndexed { ri, ring ->
                ring.forEachIndexed { vi, pos ->
                    val projected = map.project(arrayOf(pos.x, pos.y))
                    val px = projected.x as Double
                    val py = projected.y as Double
                    if (px >= rect.left && px <= rect.right && py >= rect.top && py <= rect.bottom) {
                        toDelete.add(Triple(pi, ri, vi))
                    }
                }
            }
        }
        toDelete.groupBy { it.first to it.second }.forEach { (key, triples) ->
            val (pi, ri) = key
            val ring = editParts.getOrNull(pi)?.getOrNull(ri) ?: return@forEach
            // Remove highest indices first so earlier removals don't shift later indices, and never
            // shrink a ring below a valid triangle.
            triples.map { it.third }.sortedDescending().forEach { vi ->
                if (ring.size > 3 && vi < ring.size) {
                    ring.removeAt(vi)
                }
            }
        }
        refreshRegionEditSources(map)
    }

    private fun dedupeRing(ring: List<Position>): List<Position> {
        val first = ring.firstOrNull()
        val last = ring.lastOrNull()
        return if (ring.size > 1 && first != null && last != null && first.x == last.x && first.y == last.y) {
            ring.dropLast(1)
        } else {
            ring
        }
    }

    private fun closedRing(ring: List<Position>): List<Position> = ring + ring.first()

    private fun editGeometry(): Geometry {
        val closedParts = editParts.map { rings -> rings.map { closedRing(it) } }
        return if (closedParts.size <= 1) {
            Polygon(coordinates = closedParts.firstOrNull() ?: emptyList())
        } else {
            MultiPolygon(coordinates = closedParts)
        }
    }

    private fun editPointsFeatureCollection(): FeatureCollection {
        val features = mutableListOf<Feature>()
        editParts.forEachIndexed { pi, rings ->
            rings.forEachIndexed { ri, ring ->
                ring.forEachIndexed { vi, pos ->
                    features.add(
                        Feature(
                            geometry = Point(pos),
                            properties = buildJsonObject {
                                put("part", pi)
                                put("ring", ri)
                                put("vertex", vi)
                            },
                        )
                    )
                }
            }
        }
        return FeatureCollection(features)
    }

    private fun regionEditFillLayer() = Layer(
        id = "region_edit_fill",
        type = LayerType.FILL,
        source = "region_edit",
        paint = Paint(
            fillColor = JsonPrimitive(regionColor()),
            fillOpacity = 0.5f,
        )
    )

    private fun regionEditOutlineLayer() = Layer(
        id = "region_edit_outline",
        type = LayerType.LINE,
        source = "region_edit",
        paint = Paint(
            lineColor = JsonPrimitive(regionColor()),
            lineWidth = 1.0f,
        )
    )

    private fun regionEditPointColor(): String = if (deleteMode) "#000000" else "#FF0000"

    private fun regionEditPointStrokeColor(): String = if (deleteMode) "#444444" else "#8B0000"

    private fun regionEditPointRadius(): Float = if (deleteMode) 10f else 20f

    private fun regionEditPointsLayer() = Layer(
        id = "region_edit_points",
        type = LayerType.CIRCLE,
        source = "region_edit_points",
        paint = Paint(
            circleColor = JsonPrimitive(regionEditPointColor()),
            circleRadius = regionEditPointRadius(),
            circleOpacity = 1f,
            circleStrokeColor = JsonPrimitive(regionEditPointStrokeColor()),
            circleStrokeWidth = 2f,
        )
    )

    private fun deleteVertex(part: Int, ring: Int, vertex: Int) {
        val r = editParts.getOrNull(part)?.getOrNull(ring) ?: return
        if (r.size <= 3) return
        if (vertex < r.size) r.removeAt(vertex)
    }

    private fun insertVertex(part: Int, ring: Int, vertex: Int, clockwise: Boolean) {
        val r = editParts.getOrNull(part)?.getOrNull(ring) ?: return
        val n = r.size
        if (n < 2 || vertex >= n) return
        val neighborIndex = if (clockwise) (vertex + 1) % n else (vertex - 1 + n) % n
        val current = r[vertex]
        val neighbor = r[neighborIndex]
        val mid = Position((current.x + neighbor.x) / 2.0, (current.y + neighbor.y) / 2.0)
        val insertAt = if (clockwise) vertex + 1 else vertex
        r.add(insertAt, mid)
    }

    private fun ringToWkt(ring: List<Position>): String =
        "(" + closedRing(ring).joinToString(",") { "${it.x} ${it.y}" } + ")"

    private fun regionWkt(): String {
        return if (editParts.size <= 1) {
            "POLYGON (" + (editParts.firstOrNull()?.joinToString(",") { ringToWkt(it) } ?: "") + ")"
        } else {
            "MULTIPOLYGON (" + editParts.joinToString(",") { rings ->
                "(" + rings.joinToString(",") { ringToWkt(it) } + ")"
            } + ")"
        }
    }

    private fun copyRegionWkt() {
        window.navigator.asDynamic().clipboard.writeText(regionWkt())
    }

    private var vertexContextMenuDismiss: EventListener? = null

    private fun removeVertexContextMenu() {
        document.getElementById("region-edit-context-menu")?.remove()
        vertexContextMenuDismiss?.let { document.removeEventListener("click", it) }
        vertexContextMenuDismiss = null
    }

    private fun showVertexContextMenu(map: MapLibre.Map, x: Int, y: Int, part: Int, ring: Int, vertex: Int) {
        removeVertexContextMenu()
        val (offsetX, offsetY) = containerOffset()
        val menu = document.createElement("div") as HTMLDivElement
        menu.id = "region-edit-context-menu"
        menu.style.cssText =
            "position:fixed;left:${offsetX + x}px;top:${offsetY + y}px;background:#fff;color:#000;" +
                "border:1px solid #888;border-radius:4px;box-shadow:0 2px 8px rgba(0,0,0,0.3);" +
                "z-index:10000;font-size:14px;min-width:120px;overflow:hidden;"

        fun addItem(label: String, action: () -> Unit) {
            val item = document.createElement("div") as HTMLDivElement
            item.textContent = label
            item.style.cssText = "padding:8px 12px;cursor:pointer;"
            item.addEventListener("mouseenter", EventListener { item.style.backgroundColor = "#eee" })
            item.addEventListener("mouseleave", EventListener { item.style.backgroundColor = "#fff" })
            item.addEventListener("click", EventListener {
                action()
                removeVertexContextMenu()
            })
            menu.appendChild(item)
        }

        addItem("Copy WKT") { copyRegionWkt() }
        addItem("Delete") {
            deleteVertex(part, ring, vertex)
            refreshRegionEditSources(map)
        }
        addItem("Add CW") {
            insertVertex(part, ring, vertex, clockwise = true)
            refreshRegionEditSources(map)
        }
        addItem("Add CCW") {
            insertVertex(part, ring, vertex, clockwise = false)
            refreshRegionEditSources(map)
        }

        document.body?.appendChild(menu)

        val dismiss = EventListener { evt ->
            val target = evt.target as? Node
            if (target == null || !menu.contains(target)) {
                removeVertexContextMenu()
            }
        }
        vertexContextMenuDismiss = dismiss
        window.setTimeout({ document.addEventListener("click", dismiss) }, 0)
    }

    private fun addSectorImage(map: MapLibre.Map, name: String) {
        val parts = name.removePrefix("sector_").split("_")
        if (parts.size < 9) return
        val sectr1 = parts[0].toDoubleOrNull() ?: return
        val sectr2 = parts[1].toDoubleOrNull() ?: return
        val dayColor = parts[2]
        val duskColor = parts[3]
        val nightColor = parts[4]
        val dayLineColor = parts[5]
        val duskLineColor = parts[6]
        val nightLineColor = parts[7]
        val radius = parts[8].toDoubleOrNull() ?: 80.0

        val (fillColor, lineColor) = when (themeMode) {
            ThemeMode.Day -> dayColor to dayLineColor
            ThemeMode.Dusk -> duskColor to duskLineColor
            ThemeMode.Night -> nightColor to nightLineColor
            null -> dayColor to dayLineColor
        }

        val svg = buildSectorSvg(sectr1, sectr2, fillColor, lineColor, radius)
        val dataUrl = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg)
        val img = js("new Image(200, 200)")
        img.onload = { map.addImage(name, img) }
        img.src = dataUrl
    }

    fun disposeMapView() {
        mapView?.remove()
        mapView = null
        scaleControl = null
    }

    private fun mapLibreArgs(
        container: HTMLDivElement,
    ): dynamic {
        val state = chartViewModel.flow.value
        val obj = js("{}")
        obj["container"] = container
        obj["style"] = stylePath(state.theme, state.depth)
        obj["center"] = arrayOf(state.location.longitude, state.location.latitude)
        obj["zoom"] = state.location.zoom
        obj["attributionControl"] = false
        return obj
    }

    @OptIn(ExperimentalSerializationApi::class)
    actual fun highlight(feature: Feature) {
        mapView?.let {
            val source = Source(
                type = SourceType.GEOJSON,
                data = feature,
            )
            val f = json.encodeToDynamic(source)
            println("highlighting feature: $f")
            it.addSource("highlight", f)
        }
    }

    actual fun project(mapLocation: MapLocation): MapPoint? {
        return mapView?.let { mapView ->
            val p = mapView.project(arrayOf(mapLocation.longitude, mapLocation.latitude))
            MapPoint(p.x, p.y)
        }
    }
}

val highlightLine = Layer(
    id = "highlight_line",
    type = LayerType.LINE,
    source = "highlight",
    layout = Layout(
        lineJoin = LineJoin.ROUND,
        lineCap = LineCap.ROUND
    ),
    paint = Paint(
        lineColor = JsonPrimitive("#D63F24"),
        lineWidth = 8.0f
    )
)

val highlightPoint = Layer(
    id = "highlight_point",
    type = LayerType.CIRCLE,
    source = "highlight",
    filter = JsonArray(listOf(JsonPrimitive("=="), JsonPrimitive("\$type"), JsonPrimitive("Point"))),
    paint = Paint(
        circleStrokeColor = JsonPrimitive("#D63F24"),
        circleOpacity = 0f,
        circleRadius = 80f,
        circleStrokeWidth = 8.0f,
    )
)
