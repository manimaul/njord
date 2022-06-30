package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.SymbolLayerLibrary
import io.madrona.njord.model.ChartFeature
import io.madrona.njord.util.logger
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer

abstract class Layerable(
    private val autoSymbol: Boolean = false,
    private val symbolLayerLibrary: SymbolLayerLibrary = Singletons.symbolLayerLibrary,
) {
    val log = logger()
    val key = javaClass.simpleName.uppercase()
    abstract fun layers(options: LayerableOptions): Sequence<Layer>

    open fun tileEncode(feature: ChartFeature) = Unit

    fun preTileEncode(feature: ChartFeature) {
        if (feature.layer == key) {
            if (autoSymbol) {
                val sy = symbolLayerLibrary.symbol(key, feature.props)
                feature.props["SY"] = sy
            }
            tileEncode(feature)
        }
    }
}

data class LayerableOptions(
    val depth: Depth
)
