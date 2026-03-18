package io.madrona.njord.layers

import io.madrona.njord.model.*

/**
 * Geometry Primitives: Area
 *
 * Object: Gridiron
 *
 * Acronym: GRIDRN
 *
 * Code: 60
 */
class Gridrn : Layerable() {

//    S-52 Display Standards (interpretation)
//    According to the S-52 Presentation Library, the display of a GRIDRN depends on its geometry and the mariner's selected symbol style:
//    Point Geometry:
//    Paper Chart Symbols: Displays using a specific symbol resembling a small grid (often designated as GRIDRN01).
//    Simplified Symbols: Displays as a standardized circular or square point symbol, though most ECDIS systems prefer the specific gridiron icon for clarity.
//    Area Geometry:
//    Boundary: The polygon is outlined with a thin, solid line (typically CHMGD Magenta or CSTLN Shoreline color, depending on its location relative to the high-water mark).
//    Pattern Fill: The area is typically filled with a diagonal grid pattern or a specific hash pattern (e.g., SQUARES) to indicate it is a structured area rather than just a patch of seabed.
//    Viewing Group & Priority:
//    Display Category: It is generally classified under the Standard Display or Other category, meaning it may not be visible in the "Base Display" mode unless explicitly enabled.
//    Color Tokens: Uses standard S-52 color tokens like CHMGD (Magenta) for man-made features or LANDF (Land Features) if it is considered a coastal structure.
    override fun layers(options: LayerableOptions) = sequenceOf(
        lineLayerWithColor(theme = options.theme, color = Color.CHMGD, width = 1f),
    )
}
