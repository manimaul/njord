package io.madrona.njord.viewmodel

import io.madrona.njord.model.ChartCatalog
import io.madrona.njord.model.ChartItem
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.launch

data class ChartCatalogState(
    val pageIndex: Int = 0,
    val catalog: Async<ChartCatalog> = Uninitialized,
    val filter: String? = null,
    val filtered: List<ChartItem> = emptyList(),
)

val chartCatalogViewModel = ChartCatalogViewModel()

class ChartCatalogViewModel : BaseViewModel<ChartCatalogState>(ChartCatalogState()) {

    init {
        reload()
    }

    override fun reload() {
        setState { copy(catalog = Loading()) }

        setState {
            val cat = Network.getChartCatalog(0).toAsync()
            copy(
                catalog = cat,
                filtered = cat.value?.page?.let { filterItems(filter, it) } ?: emptyList(),
            )
        }
    }

    fun loadMore() {
        launch {
            withStateAsync { state ->
                when (val catalog = state.catalog) {
                    is Complete<ChartCatalog>,
                    is Fail<ChartCatalog> -> {
                        val page = catalog.value?.page ?: emptyList()
                        setState { copy(catalog = Loading(catalog.value)) }
                        val combinedCatalog =
                            Network.getChartCatalog(catalog.value?.nextId ?: 0).toAsync().map { response ->
                                response.copy(page = page + response.page)
                            }
                        setState {
                            copy(
                                catalog = combinedCatalog,
                                filtered = combinedCatalog.value?.page?.let { filterItems(filter, it) } ?: emptyList(),
                            )
                        }
                    }

                    is Loading<ChartCatalog> -> Unit
                    Uninitialized -> reload()
                }
            }
        }
    }

    private fun filterItems(name: String?, items: List<ChartItem>): List<ChartItem> {
        return items.filter { item ->
            name?.let { item.name.contains(it, true) } ?: true
        }
    }

    fun setPageIndex(pageIndex: Int) {
        setState { copy(pageIndex = pageIndex) }
    }

    fun filter(value: String) {
        println("setting filter: $value")
        val f = value.takeIf { it.isNotBlank() }
        setState {
            copy(
                filter = f,
                filtered = catalog.value?.page?.let { filterItems(f, it) } ?: emptyList()
            )
        }
    }
}