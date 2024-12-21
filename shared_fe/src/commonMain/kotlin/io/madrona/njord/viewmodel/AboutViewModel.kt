package io.madrona.njord.viewmodel

import io.madrona.njord.model.AboutJson
import io.madrona.njord.network.Network
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import io.madrona.njord.viewmodel.utils.toAsync

data class AboutState(
    val response: Async<AboutJson> = Uninitialized
)

class AboutViewModel : BaseViewModel<AboutState>(AboutState()) {
    init { reload() }

    override fun reload() {
        setState { copy(response = Loading()) }
        setState { copy(response = Network.getAbout().toAsync()) }
    }
}