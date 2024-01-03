package io.madrona.njord.layers

class Ctrpnt : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
