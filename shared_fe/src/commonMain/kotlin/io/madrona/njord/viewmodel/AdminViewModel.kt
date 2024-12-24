package io.madrona.njord.viewmodel

import io.madrona.njord.model.AdminResponse
import io.madrona.njord.model.AdminSignature
import io.madrona.njord.network.Network
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import io.madrona.njord.viewmodel.utils.*

data class AdminState(
    val adminSignature: Async<AdminResponse> = localStoreGet<AdminResponse>()?.let { Complete(it) } ?: Uninitialized,
) {
    val isLoggedIn: Boolean
        get() = adminSignature is Complete
}

val adminViewModel = AdminViewModel()

class AdminViewModel : BaseViewModel<AdminState>(AdminState()) {
    init {
        setState {
            copy(adminSignature = adminSignature.flatMap {
                Network.verifyAdmin(it.signature).toAsync(Uninitialized)
            })
        }
    }

    override fun reload() {}

    val signature: AdminResponse?
        get() = flow.value.adminSignature.value

    fun login() {
        setState {
            val signature = Network.getAdmin().toAsync().flatMap {
                Network.verifyAdmin(it.signature).toAsync()
            }
            signature.value?.let { localStoreSet<AdminResponse>(it) }
            copy(adminSignature = signature)
        }

    }

    fun logout() {
        localStoreSet<AdminSignature>(null)
        setState { copy(adminSignature = Uninitialized) }
    }
}