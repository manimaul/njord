package io.madrona.njord.layers

import io.madrona.njord.layers.attributehelpers.Catlmk
import io.madrona.njord.layers.attributehelpers.Catlmk.Companion.catlmk
import io.madrona.njord.layers.attributehelpers.Convis
import io.madrona.njord.layers.attributehelpers.Convis.Companion.convis
import io.madrona.njord.layers.attributehelpers.Functn
import io.madrona.njord.layers.attributehelpers.Functn.Companion.functn
import io.madrona.njord.model.*

/**
 * Geometry Primitives: Point, Line, Area
 *
 * Object: Landmark
 *
 * Acronym: LNDMRK
 *
 * Code: 74
 */
class Lndmrk : Layerable() {
    override fun preTileEncode(feature: ChartFeature) {
        val functn = feature.functn()
        var viz = false
        when (feature.convis()) {
            Convis.VISUAL_CONSPICUOUS -> {
                viz = true
                feature.areaColor(Color.CHBRN)
                feature.lineColor(Color.CHBLK)
            }

            Convis.NOT_VISUAL_CONSPICUOUS, null -> {
                feature.areaColor(Color.CHBRN)
                feature.lineColor(Color.LANDF)
            }
        }

        if (functn.any { it == Functn.CHURCH || it == Functn.CHAPEL }) {
            if (viz) {
                feature.pointSymbol(Sprite.BUIREL13)
            } else {
                feature.pointSymbol(Sprite.BUIREL01)
            }
            return
        }

        if (functn.any { it == Functn.MOSQUE || it == Functn.MARABOUT }) {
            if (viz) {
                feature.pointSymbol(Sprite.BUIREL15)
            } else {
                feature.pointSymbol(Sprite.BUIREL05)
            }
            return
        }

        when (feature.catlmk().firstOrNull()) {
            Catlmk.CAIRN -> {
                if (viz) {
                    feature.pointSymbol(Sprite.CAIRNS11)
                } else {
                    feature.pointSymbol(Sprite.CAIRNS01)
                }
            }
            Catlmk.CHIMNEY -> {
                if (viz) {
                    feature.pointSymbol(Sprite.CHIMNY11)
                } else {
                    feature.pointSymbol(Sprite.CHIMNY01)
                }
            }
            Catlmk.DISH_AERIAL -> feature.pointSymbol(Sprite.DSHAER11)
            Catlmk.FLAGSTAFF_FLAGPOLE -> feature.pointSymbol(Sprite.FLGSTF01)
            Catlmk.FLARE_STACK -> {
                if (viz) {
                    feature.pointSymbol(Sprite.FLASTK11)
                } else {
                    feature.pointSymbol(Sprite.FLASTK01)
                }
            }
            Catlmk.MAST -> {
                if (viz) {
                    feature.pointSymbol(Sprite.MSTCON14)
                } else {
                    feature.pointSymbol(Sprite.MSTCON04)
                }
            }
            Catlmk.MONUMENT -> feature.pointSymbol(Sprite.MONUMT12)

            Catlmk.COLUMN_PILLAR,
            Catlmk.OBELISK,
            Catlmk.STATUE -> {
                feature.pointSymbol(Sprite.MONUMT12)
            }

            Catlmk.CROSS -> {
                if (viz) {
                    feature.pointSymbol(Sprite.BUIREL13)
                } else {
                    feature.pointSymbol(Sprite.BUIREL01)
                }
            }

            Catlmk.DOME -> {
                if (viz) {
                    feature.pointSymbol(Sprite.DOMES011)
                } else {
                    feature.pointSymbol(Sprite.DOMES001)
                }
            }

            Catlmk.RADAR_SCANNER -> {
                if (viz) {
                    feature.pointSymbol(Sprite.RASCAN11)
                } else {
                    feature.pointSymbol(Sprite.RASCAN01)
                }
            }

            Catlmk.TOWER -> {
                if (viz) {
                    feature.pointSymbol(Sprite.TOWERS12)
                } else {
                    feature.pointSymbol(Sprite.TOWERS02)
                }
            }

            Catlmk.WINDMILL -> {
                feature.pointSymbol(Sprite.WNDMIL12)
            }
            Catlmk.WINDMOTOR -> {
                if (viz) {
                    feature.pointSymbol(Sprite.WIMCON11)
                } else {
                    feature.pointSymbol(Sprite.WIMCON01)
                }
            }

            Catlmk.MEMORIAL_PLAQUE,
            Catlmk.CEMETERY,
            Catlmk.WINDSOCK,
            Catlmk.SPIRE_MINARET,
            null -> {
                if (viz) {
                    feature.pointSymbol(Sprite.POSGEN03)
                } else {
                    feature.pointSymbol(Sprite.POSGEN01)
                }
            }
        }

    }

    override fun layers(options: LayerableOptions) = sequenceOf(
        areaLayerWithFillColor(),
        areaLayerWithSingleSymbol(),
        lineLayerWithColor(width = 1f),
        pointLayerFromSymbol(),
    )
}
