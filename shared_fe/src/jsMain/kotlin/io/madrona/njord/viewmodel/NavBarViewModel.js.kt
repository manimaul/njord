package io.madrona.njord.viewmodel

import io.madrona.njord.network.Network
import io.madrona.njord.network.map
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import io.madrona.njord.viewmodel.utils.toAsync
import kotlinx.coroutines.launch

data class NavBarState(
    val customColors: Async<List<String>> = Uninitialized,
)

class NavBarViewModel : BaseViewModel<NavBarState>(NavBarState()) {
    init {
        reload()
        launch {
            flow.collect {
                println("NavBarViewModel state: $it")
            }
        }
    }
    override fun reload() {
        launch {
            setState { copy(customColors = Loading()) }
            setState {
                copy(
                    customColors = Network.getCustomColors()
                        .map { cc ->
                            cc.keys.toMutableList().also {
                                it.add(0, "Default")
                            }
                        }.toAsync()
                )
            }
        }
    }
}