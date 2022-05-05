package io.madrona.njord.layers

import io.madrona.njord.layers.set.BaseLayers
import io.madrona.njord.layers.set.StandardLayers

class LayerFactory(
    private val baseLayers: BaseLayers = BaseLayers(),
    private val standardLayers: StandardLayers = StandardLayers()
//    private val layerables: Sequence<Layerable> = sequenceOf(
//        Background(),
//        Seaare(),
//        Depare(),
//        Depcnt(),
//        NavLne(),
//        Fairwy(),
//        Slcons(),
//        Ponton(),
//        Pilpnt(),
//        Hulkes(),
//        Lndare(),
//        Coalne(),
//        Boyspp(),
//        Achare(),
//        Achbrt(),
//        Achpnt(),
//        Airare(),
//        Bcncar(),
//        Bcnisd(),
//        Bcnlat(),
//        Bcnsaw(),
//        Bcnspp(),
//        Berths(),
//        Boycar(),
//        Boyinb(),
//        Boyisd(),
//        Boylat(),
//        Boysaw(),
//        Bridge(),
//        Buaare(),
//        Buirel(),
//        Buisgl(),
//        Cgusta(),
//        Chkpnt(),
//        Cranes(),
//        Ctnare(),
//        Ctrpnt(),
//        Ctsare(),
//        Curent(),
//        Damcon(),
//        Daymar(),
//        Dismar(),
//        Dmpgrd(),
//        Fogsig(),
//        Forstc(),
//        Fshfac(),
//        Gatcon(),
//        Morfac(),
//        Lights(), //symbol lookup
//        Soundg(),
//        Ply(),
//        Debug()
//    )
) {

    fun layers(options: LayerableOptions) = (
            baseLayers.layers + standardLayers.layers).map {
        it.layers(options)
    }.flatten().toList()
}