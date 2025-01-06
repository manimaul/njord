package io.madrona.njord.viewmodel

import io.madrona.njord.model.IconInfo
import io.madrona.njord.model.Sprite
import io.madrona.njord.model.ThemeMode
import io.madrona.njord.model.mode
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import io.madrona.njord.viewmodel.utils.toAsync
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SpriteState(
    val sheet: Async<Map<Sprite, IconInfo>> = Uninitialized,
    val sheetUrl: String? = null
)

val spriteViewModel = SpriteViewModel()

class SpriteViewModel : BaseViewModel<SpriteState>(SpriteState()) {
    init {
        launch {
            chartViewModel.flow.map { it.theme.mode() }.collect {
                selectSheet(it)
            }
        }
    }

    private fun selectSheet(mode: ThemeMode) {
        setState {
            copy(
                sheet = Loading(),
                sheetUrl = null,
            )
        }
        setState {
            copy(
                sheet = Network.getSpriteSheet(mode).toAsync(),
                sheetUrl = "/v1/content/sprites/${mode.name.lowercase()}_simplified@2x.png",
            )
        }
    }

    override fun reload() {
        selectSheet(chartViewModel.flow.value.theme.mode())
    }
}