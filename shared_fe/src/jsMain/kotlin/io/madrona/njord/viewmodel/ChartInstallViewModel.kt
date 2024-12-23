package io.madrona.njord.viewmodel

import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.util.json
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import kotlinx.browser.window
import org.w3c.dom.WebSocket
import org.w3c.dom.events.EventListener
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest


external fun encodeURIComponent(str: String): String

fun websocketUri(upload: EncUpload): String? {
    return adminViewModel.signature?.let { sig ->
        val loc = window.location.host.let {
            if (it.endsWith(":8080")) {
                window.location.hostname + ":9000"
            } else {
                it
            }
        }
        val base = if (window.location.protocol == "https") {
            "wss://$loc"
        } else {
            "ws://$loc"
        }
        val files = upload.files.fold("") { acc, ea ->
            acc + "&file=${encodeURIComponent(ea)}"
        }
        "$base/v1/ws/enc_process?uuid=${encodeURIComponent(upload.uuid)}&signature=${sig.signatureEncoded}$files"
    }

}

data class ChartInstallState(
    val uploadProgress: Int = 0,
    val encUpload: Async<EncUpload> = Uninitialized,
    val info: WsMsg? = null,
    val webSocket: WebSocket? = null
)

val chartInstallViewModel = ChartInstallViewModel()

class ChartInstallViewModel : BaseViewModel<ChartInstallState>(ChartInstallState()) {
    override fun reload() {
    }

    fun upload(formData: FormData) {
        withState { state ->
            if (state.encUpload == Uninitialized) {
                startUpload(formData)
            }
        }
    }

    private fun startUpload(formData: FormData) {
        setState { copy(encUpload = Loading()) }
        val xhr = XMLHttpRequest();
        xhr.upload.addEventListener("progress", EventListener {
            val loaded = it.asDynamic().loaded as Double
            val total = it.asDynamic().total as Double
            val percent = ((loaded / total) * 100).toInt()
            setState { copy(uploadProgress = percent) }
        })
        xhr.addEventListener("load", EventListener {
            val encUpload = json.decodeFromString<EncUpload>(xhr.responseText)
            setState {
                copy(
                    encUpload = Complete(encUpload),
                    uploadProgress = 100
                )
            }
            setupWebSocket(encUpload)
        })
        val url = "/v1/enc_save?signature=${adminViewModel.signature?.signatureEncoded}"
        xhr.open("POST", url, true)
        xhr.send(formData)
    }

    private fun setupWebSocket(encUpload: EncUpload) {
        websocketUri(encUpload)?.let { uri ->
            val ws = WebSocket(uri)
            ws.onclose = {
                setState { copy(webSocket = null) }
            }
            ws.onerror = {
                setState { copy(webSocket = null) }
            }
            ws.onopen = {
                setState { copy(webSocket = ws) }
            }
            ws.onmessage = { event ->
                (event.data as? String)?.let { json.decodeFromString<WsMsg>(it) }?.let {
                    setState { copy(info = it) }
                }
            }
        }
    }
}