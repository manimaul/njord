package io.madrona.njord.viewmodel

import io.madrona.njord.model.AdminResponse
import io.madrona.njord.network.Network
import io.madrona.njord.util.localStoreGet
import io.madrona.njord.util.localStoreSet
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class AdminState(
    val adminSignature: Async<AdminResponse> = localStoreGet<AdminResponse>()?.let { Complete(it) } ?: Uninitialized,
    val adminSignatureRemaining: Duration? = null,
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
        clock()
    }

    private fun clock() {
        launch {
            delay(1000)
            setState {
                val duration = adminSignature.value?.signature?.let {
                    val now = Clock.System.now().epochSeconds
                    (it.expirationDate.epochSeconds - now).toDuration(DurationUnit.SECONDS)
                }
                copy(adminSignatureRemaining = duration)
            }
            clock()
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
        localStoreSet<AdminResponse>(null)
        setState { copy(adminSignature = Uninitialized) }
    }
}