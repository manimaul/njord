package io.madrona.njord.layers

import io.madrona.njord.ext.json
import io.madrona.njord.geo.symbols.doubleValue
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.Color
import io.madrona.njord.model.Layer
import kotlin.math.roundToInt

/**
 * Feature property carrying the S-52 bearing text for line objects whose presentation library
 * lookup has `TE('%03.0lf deg','ORIENT',...)` - NAVLNE, RADLNE, RECTRC, RCRTCL and DWRTCL (text
 * group 11, "important text").
 *
 * Pre-rendered into the tile rather than composed by a style expression because the style spec
 * has no zero-padding, and a bearing has no unit preference to keep the tile agnostic of.
 */
const val BEARING = "_BRG"

fun ChartFeature.bearingLabel() {
    props.doubleValue("ORIENT")?.let { props[BEARING] = formatBearing(it).json }
}

/** `%03.0lf deg` - whole degrees, zero padded to three digits. */
fun formatBearing(orient: Double): String = "${orient.roundToInt().toString().padStart(3, '0')} deg"

fun Layerable.bearingLabelLayer(options: LayerableOptions): Layer = lineLayerWithLabel(
    label = Label.Property(BEARING),
    theme = options.theme,
    labelColor = Color.CHBLK,
    highlightColor = Color.CHWHT,
)
