package io.madrona.njord.layers

class Buirel : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
