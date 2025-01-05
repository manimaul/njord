package io.madrona.njord.viewmodel

import io.madrona.njord.model.ChartCatalog
import io.madrona.njord.model.ChartItem
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChartCatalogState(
    val deleteProgress: Int? = null,
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
        setState { ChartCatalogState(catalog = Loading()) }
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

    fun progress(n: Int, total: Int) : Int {
        return (n.toFloat() / total.toFloat() * 100).toInt()
    }

    fun deleteAll() {
        adminViewModel.signature?.let { sig ->
            val delete = withState {
               it.filtered
            }
            setState {
                copy(deleteProgress = 1)
            }
            launch {
                delete.forEachIndexed { i, item->
                    Network.deleteChart(sig, item.id)
                    setState {
                        copy(deleteProgress = progress(i + 1, delete.size))
                    }
                }
            }
        }
    }
}