package io.madrona.njord.layers

import floatValue
import io.madrona.njord.Singletons
import io.madrona.njord.layers.attributehelpers.Catwrk
import io.madrona.njord.layers.attributehelpers.Catwrk.Companion.catwrk
import io.madrona.njord.layers.attributehelpers.Watlev
import io.madrona.njord.layers.attributehelpers.Watlev.Companion.watlev
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Area
 *
 * Object: Wreck
 *
 * Acronym: WRECKS
 *
 * Code: 159
 */
class Wrecks(
    val safetyDepth: Float = Singletons.config.safetyDepth
) : Layerable() {

    override suspend fun preTileEncode(feature: ChartFeature) {
        //WRECKS01	(Hull showing above water)	Visible above the surface
        //WRECKS05	(Fishbone with dotted circle)	Dangerous, submerged, depth unknown
        //WRECKS04  (Fishbone) Non-dangerous,
        //DANGER01	(Hazard symbol with depth text)	Depth known,￼ ≤Safety Depth
        //ISODGR01	(Magenta "screw head")	Hazard in otherwise safe water

        when(feature.catwrk()) {
            Catwrk.DANGEROUS_WRECK -> {
                feature.pointSymbol(Sprite.WRECKS05)
            }

            Catwrk.WRECK_SHOWING_MAST_MASTS,
            Catwrk.WRECK_SHOWING_ANY_PORTION_OF_HULL_OR_SUPERSTRUCTURE -> {
                feature.pointSymbol(Sprite.WRECKS01)
            }

            Catwrk.DISTRIBUTED_REMAINS_OF_WRECK,
            Catwrk.NON_DANGEROUS_WRECK,
            null -> {
                val underSafety = feature.props.floatValue("VALSOU")?.let {
                    it > safetyDepth
                }
                if (feature.watlev() == Watlev.ALWAYS_UNDER_WATER_SUBMERGED && underSafety == null || underSafety == true) {
                    feature.pointSymbol(Sprite.WRECKS04)
                } else if (underSafety == false) {
                    feature.pointSymbol(Sprite.DANGER01)
                } else {
                    feature.pointSymbol(Sprite.ISODGR01)
                }
            }
        }

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(
            anchor = Anchor.CENTER,
            iconRotationAlignment = IconRotationAlignment.MAP
        )
    )
}
