package io.madrona.njord.layers

class Berths : Layerable() {
    override fun layers(options: LayerableOptions) = sequenceOf(
        pointLayerFromSymbol(),
    )
}
