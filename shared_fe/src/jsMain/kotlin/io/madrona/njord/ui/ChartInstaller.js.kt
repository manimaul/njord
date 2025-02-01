package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.model.ws.WsMsg
import io.madrona.njord.viewmodel.ChartInstallState
import io.madrona.njord.viewmodel.adminViewModel
import io.madrona.njord.viewmodel.chartInstallViewModel
import io.madrona.njord.viewmodel.utils.Complete
import io.madrona.njord.viewmodel.utils.Fail
import io.madrona.njord.viewmodel.utils.Loading
import io.madrona.njord.viewmodel.utils.Uninitialized
import org.jetbrains.compose.web.attributes.*
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLFormElement
import org.w3c.xhr.FormData

@Composable
fun ChartInstaller() {
    val state by chartInstallViewModel.flow.collectAsState()
    val adminState by adminViewModel.flow.collectAsState()
    if (adminState.isLoggedIn) {
        when (val upload = state.encUpload) {
            is Complete -> ChartInstallProgress(state)
            is Fail -> ErrorDisplay(upload) { }
            is Loading -> Progress("Uploading chart file", state.uploadProgress)
            Uninitialized -> ChartInstallForm()
        }
    } else {
        Text("Admin access required")
    }
}

private fun WsMsg.Info.text() : String {
    return "Installed: features $feature of $totalFeatures - charts $chart of $totalCharts"
}

@Composable
fun ChartInstallProgress(state: ChartInstallState) {
    when (val wsMsg = state.info) {
        is WsMsg.CompletionReport -> {
            println("complete $wsMsg")
            Text("$wsMsg")
        }
        is WsMsg.Error -> {
            println("info $wsMsg")
            Text("$wsMsg")
        }
        is WsMsg.Extracting -> {
            val progress = (wsMsg.progress * 100.0).toInt()
            Progress("Extracting chart files", progress)
        }
        is WsMsg.Info -> {
            val progress = ((wsMsg.feature.toDouble() / wsMsg.totalFeatures.toDouble()) * 100.0).toInt()
            Progress(wsMsg.text(), progress)
        }
        null -> {
            println("null wsMsg")
            Text("null wsMsg")
        }
    }
}

@Composable
fun Progress(
    title: String,
    progress: Int,
) {
    H6 { Text(title) }
    Div(attrs = {
        classes("progress")
        attr("role", "progressbar")
        attr("aria-label", title)
        attr("aria-valuenow", "$progress")
        attr("aria-valuemin", "0")
        attr("aria-valuemax", "100")
    }) {
        Div(attrs = {
            style { width(progress.percent) }
            classes("progress-bar", "w-$progress")
        })
    }
}

@Composable
fun ChartInstallForm() {
    var url: String? by remember { mutableStateOf(null) }
    var form: HTMLFormElement? by remember { mutableStateOf(null) }
    Form(attrs = {
        ref {
            form = it
            object : DisposableEffectResult {
                override fun dispose() {
                    form = null
                }
            }
        }
    }) {

        Div(attrs = {
            classes("mb-3")
        }) {
            Label(attrs = {
                classes("form-label")
                attr("for", "encZip")
            }) {
                Text("ENC Chart(s) Zip File")
            }
            Input(InputType.File, attrs = {
                id("encZip")
                accept("application/zip")
                classes("form-control")
                name("enczip")
                onChange {
                    println("onChange ${it.target.value}")
                    url = it.target.value
                }
            })
        }
        Button(attrs = {
            type(ButtonType.Submit)
            url?.let {
                classes("btn", "btn-primary")
            } ?: classes("btn", "btn-primary", "disabled")
            onClick {
                it.preventDefault()
                form?.let {
                    chartInstallViewModel.upload(FormData(it))
                }
            }
        }) { Text("Submit") }
    }
}