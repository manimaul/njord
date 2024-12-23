package io.madrona.njord.viewmodel

import io.madrona.njord.model.ChartCatalog
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.launch

data class ChartCatalogState(
    val pageIndex: Int = 0,
    val catalog: Async<ChartCatalog> = Uninitialized,
)

val chartCatalogViewModel = ChartCatalogViewModel()

class ChartCatalogViewModel : BaseViewModel<ChartCatalogState>(ChartCatalogState()) {

    init {
        reload()
    }

    override fun reload() {
        setState { copy(catalog = Loading()) }
        setState { copy(catalog = Network.getChartCatalog(0).toAsync()) }
    }

    fun loadMore() {
        launch {
            withStateAsync { state ->
                when (val catalog = state.catalog) {
                    is Complete<ChartCatalog>,
                    is Fail<ChartCatalog> -> {
                        val page = catalog.value?.page ?: emptyList()
                        setState { copy(catalog = Loading(catalog.value)) }
                        setState {
                            copy(catalog = Network.getChartCatalog(catalog.value?.nextId ?: 0).toAsync().map { response ->
                                response.copy(page = page + response.page)
                            })
                        }
                    }
                    is Loading<ChartCatalog> -> Unit
                    Uninitialized -> reload()
                }
            }
        }
    }

    fun setPageIndex(pageIndex: Int) {
        setState { copy(pageIndex = pageIndex) }
    }
}