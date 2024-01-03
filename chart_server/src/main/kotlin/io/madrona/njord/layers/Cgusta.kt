package io.madrona.njord.layers

class Cgusta : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
