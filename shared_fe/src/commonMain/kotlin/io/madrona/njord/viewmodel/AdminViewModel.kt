package io.madrona.njord.viewmodel

import io.madrona.njord.model.AdminResponse
import io.madrona.njord.model.AdminSignature
import io.madrona.njord.network.Network
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import io.madrona.njord.viewmodel.utils.*

data class AdminState(
    val adminSignature: Async<AdminResponse> = localStoreGet<AdminResponse>()?.let { Complete(it) } ?: Uninitialized,
)

val adminViewModel = AdminViewModel()

class AdminViewModel : BaseViewModel<AdminState>(AdminState()) {
    override fun reload() {}

    fun login() {
        setState {
            val signature = Network.getAdmin().toAsync().flatMap {
                Network.verifyAdmin(it.signature).toAsync()
            }
            signature.value?.let { localStoreSet<AdminResponse>(it) }
            println("setting signature $signature")
            copy(adminSignature = signature)
        }

    }

    fun logout() {
        localStoreSet<AdminSignature>(null)
        setState { copy(adminSignature = Uninitialized) }
    }
}