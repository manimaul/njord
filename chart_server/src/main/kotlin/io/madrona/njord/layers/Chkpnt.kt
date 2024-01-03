package io.madrona.njord.layers

class Chkpnt : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
