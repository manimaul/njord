package io.madrona.njord.layers

import io.madrona.njord.Singletons
import io.madrona.njord.geo.symbols.S57Prop
import io.madrona.njord.geo.symbols.SymbolLayerLibrary
import io.madrona.njord.util.logger
import io.madrona.njord.model.Depth
import io.madrona.njord.model.Layer
import io.madrona.njord.model.StyleColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface Layerable {
    val key: String
    fun layers(options: LayerableOptions): Sequence<Layer>
}

@Suppress("LeakingThis")
abstract class SymbolLayerable(
    private val library: SymbolLayerLibrary = Singletons.symbolLayerLibrary
): Layerable {

    private val log = logger()

    init {
        val thiz = this
        Singletons.ioScope.launch {
            delay(1000)
            Singletons.symbolLayers[key] = thiz
        }
    }

    fun addSymbol(props: S57Prop) {
        val sy = library.symbol(key, props)
        log.debug("finding symbol for layer $key = $sy")
        props["SY"] = sy
    }
}

data class LayerableOptions(
        val color: StyleColor,
        val depth: Depth
)