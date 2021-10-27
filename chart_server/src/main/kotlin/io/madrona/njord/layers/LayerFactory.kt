package io.madrona.njord.layers

class LayerFactory(
        private val layerables: Sequence<Layerable> = sequenceOf(
                Background(),
                Seaare(),
                Depare(),
                Depcnt(),
                NavLne(),
                Fairwy(),
                Slcons(),
                Ponton(),
                Pilpnt(),
                Hulkes(),
                Lndare(),
                Coalne(),
                Boyspp(),
                Bcnspp(),
                Boylat(),
                Bcnlat(),
                Lights(), //symbol lookup
                Soundg(),
                Ply(),
                Debug()
        )
) {

    fun layers(options: LayerableOptions) = layerables.map {
        it.layers(options)
    }.flatten().toList()
}