package io.madrona.njord.viewmodel

import io.madrona.njord.model.Chart
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import io.madrona.njord.viewmodel.utils.toAsync

data class ChartInfoState(
    val id: String,
    val info: Async<Chart> = Uninitialized,
)

class ChartInfoViewModel(
    val id: String
) : BaseViewModel<ChartInfoState>(ChartInfoState(id)) {
    init {
        reload()
    }
    override fun reload() {
        setState { copy(info = Loading()) }
        setState { copy(info = Network.getChartInfo(id).toAsync()) }
    }
}