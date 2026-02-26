package io.madrona.njord.viewmodel

import io.madrona.njord.model.EncUpload
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.viewmodel.utils.Async
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.WebSocket
import org.w3c.dom.events.EventListener
import org.w3c.files.File
import org.w3c.xhr.XMLHttpRequest


external fun encodeURIComponent(str: String): String

fun websocketUri(): String? {
    return adminViewModel.signature?.let { sig ->
        val loc = window.location.host.let {
            if (it.endsWith(":8080")) {
                window.location.hostname + ":9000"
            } else {
                it
            }
        }
        val base = if (window.location.protocol == "https:") {
            "wss://$loc"
        } else {
            "ws://$loc"
        }
        "$base/v1/ws/enc_process?signature=${sig.signatureEncoded}"
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

    fun connect() {
        withState { state ->
            if (state.webSocket == null) {
                setupWebSocket()
            }
        }
    }

    fun reset() {
        setState { copy(encUpload = Uninitialized, uploadProgress = 0) }
    }

    fun upload(file: File) {
        withState { state ->
            if (state.encUpload == Uninitialized) {
                startUpload(file)
            }
        }
    }

    private fun startUpload(file: File) {
        println("startUpload ${file.name}")
        setState { copy(encUpload = Loading()) }
        val xhr = XMLHttpRequest()
        xhr.upload.addEventListener("progress", EventListener {
            val loaded = it.asDynamic().loaded as Double
            val total = it.asDynamic().total as Double
            val percent = ((loaded / total) * 100).toInt()
            println("encUpload progress: $percent")
            setState { copy(uploadProgress = percent) }
        })
        xhr.addEventListener("load", EventListener {
            val encUpload = Json.decodeFromString<EncUpload>(xhr.responseText)
            println("encUpload complete: $encUpload")
            setState {
                copy(
                    encUpload = Complete(encUpload),
                    uploadProgress = 100
                )
            }
        })
        val url = "/v1/enc_save?signature=${adminViewModel.signature?.signatureEncoded}&filename=${encodeURIComponent(file.name)}"
        xhr.open("POST", url, true)
        xhr.asDynamic().send(file)
    }

    private fun setupWebSocket() {
        websocketUri()?.let { uri ->
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
                try {
                    (event.data as? String)?.let { Json.decodeFromString<WsMsg>(it) }?.let { msg ->
                        setState {
                            copy(
                                info = msg,
                                encUpload = if (msg is WsMsg.Idle) Uninitialized else encUpload,
                            )
                        }
                    }
                } catch (e: Exception) {
                    println("error: ${e.message}")
                    println("event: ${event.data}")
                }
            }
        }
    }
}
