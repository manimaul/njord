package io.madrona.njord.viewmodel

import io.madrona.njord.model.LayerQueryResultPage
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

data class LayerPageState(
    val name: String,
    val pages: Async<List<LayerQueryResultPage>> = Uninitialized,
)

class LayerPageViewModel(name: String) : BaseViewModel<LayerPageState>(LayerPageState(name)) {
    init {
        reload()
    }

    override fun reload() {
        setState { copy(pages = Loading()) }
        setState {
            copy(pages = Network.getFeatureByLayer(name, 0).toAsync().map { listOf(it) })
        }
    }

    fun nextPage() {
        setState {
            copy(
                pages = flow.map { it.pages }.filter { it.complete }.take(1).first().flatMap { lst ->
                    val id = lst.lastOrNull()?.lastId ?: 0L
                    Network.getFeatureByLayer(name, id).toAsync().map { lst.toMutableList().apply { add(it) } }
                }
            )
        }
    }
}