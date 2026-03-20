package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Watlev
import io.madrona.njord.layers.attributehelpers.Watlev.Companion.watlev
import io.madrona.njord.model.Anchor
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.model.IconRotationAlignment
import io.madrona.njord.model.Sprite

/**
 * Geometry Primitives: Point
 *
 * Object: Underwater rock / awash rock
 *
 * Acronym: UWTROC
 *
 * Code: 152
 */
class Uwtroc : Layerable() {
//    In the IHO S-52 standard, the display of the UWTROC (Underwater Rock) feature is determined by the S-52 Presentation Library using look-up tables and conditional symbology procedures. The specific symbol and visibility depend on the rock's attributes and its relationship to the mariner's safety settings.
//    1. Look-Up Table Selection
//    The ECDIS "drawing engine" uses look-up tables to match S-57 object classes like UWTROC to S-52 symbols. The display depends on the feature's geometry:
//
//    Point Geometry: Most common for rocks. Symbols vary based on the rock's depth (VALSOU) or nature (WATLEV).
//    Line/Area Geometry: Used for larger rocky areas or ledges, often symbolized with specific line styles or patterns.
//    2. Primary Symbology Rules
//    The visual representation is primarily driven by the WATLEV (Water Level Effect) attribute:
//    Always Underwater (WATLEV=3): Typically shown as a "+" symbol with dots in the four quadrants.
//    Covers/Uncovers (WATLEV=4): Shown with a specific symbol (often a "+" with a single dot) to indicate it is a "rock which covers and uncovers".
//    Awash (WATLEV=5): Symbolized to show the hazard is at the water level.
//
//    3. Safety and Display Modes
//    The visibility of a UWTROC feature is categorised by its danger to the vessel:
//    Display Base: If a rock is shallower than the safety contour set by the mariner, it is considered a "danger to own-ship" and is part of the Display Base. This means it remains on the screen at all times and cannot be turned off.
//    Standard Display: Rocks that are deeper than the safety contour (not an immediate danger) are typically part of the Standard Display or "Other" category.
//    Isolated Danger Symbol: If a rock (UWTROC) is in "shallow water" (between the safety contour and the zero-meter contour), it may be highlighted by a specific Isolated Danger symbol.
//
//    4. Conditional Symbology Procedures (CSP)
//    For complex cases, such as when a rock has a specific VALSOU (Value of Sounding) or EXPSOU (Exposition of Sounding), the S-52 standard may use a CSP to calculate the correct symbol or add a depth value next to the rock symbol.
//
//    Summary Table of UWTROC Display Factors
//    Attribute / Setting 	Display Effect
//    WATLEV	Determines the basic symbol type (submerged vs. covers/uncovers).
//    VALSOU	Determines if a depth value is shown alongside the symbol.
//    Safety Contour	Determines if the rock is in the "Display Base" (always visible).
//    SCAMIN	May hide the rock at smaller scales unless it is a "danger to navigation".
//
//    SY(UWTROC01): The standard symbol for a submerged rock.
//    SY(UWTROC02): Often used for rocks that cover and uncover.
//    SY(ISDNG01): The "Isolated Danger" symbol (magenta cross) used when a rock is a specific hazard to the vessel.

    override suspend fun preTileEncode(feature: ChartFeature) {
        when (feature.watlev()) {
            Watlev.ALWAYS_UNDER_WATER_SUBMERGED -> feature.pointSymbol(Sprite.UWTROC04)

            Watlev.AWASH,
            Watlev.SUBJECT_TO_INUNDATION_OR_FLOODING,
            Watlev.PARTLY_SUBMERGED_AT_HIGH_WATER,
            Watlev.COVERS_AND_UNCOVERS -> feature.pointSymbol(Sprite.UWTROC03)

            Watlev.ALWAYS_DRY,
            Watlev.FLOATING,
            null -> feature.pointSymbol(Sprite.ISODGR01)
        }
    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP
        ),
    )
}
