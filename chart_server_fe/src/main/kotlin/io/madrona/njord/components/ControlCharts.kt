package io.madrona.njord.components

import io.madrona.njord.ChartItem
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.html.ButtonType
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.events.Event
import org.w3c.xhr.FormData
import react.Props
import react.dom.*
import react.dom.events.MouseEvent
import react.fc
import react.useEffectOnce
import react.useState

suspend fun fetchCharts(): List<ChartItem> {
    val response = window
        .fetch("/v1/chart_catalog")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

suspend fun uploadFile() {

}

val ControlCharts = fc<Props> {
    var chartList: List<ChartItem>? by useState(null)
    var messageList: List<String> by useState(emptyList())

    useEffectOnce {
        mainScope.launch {
            chartList = fetchCharts()
        }
    }

    div {
        h2 {
            +"Installed Charts"
        }

        div {
            messageList.forEach {
                div { span { +it } }
            }
        }

        form(encType = FormEncType.multipartFormData) {
            +"Add New Charts "
            div(classes = "input-group") {
                input(type = InputType.file, name = "enczip", classes = "form-control") {
                    attrs.also { input ->
                        input.accept = "application/zip"
                    }
                }
                button(type = ButtonType.button, classes = "btn btn-danger") {
                    + "Submit"
                    attrs.also { button ->
                        button.onClick = {
                            console.log("handleChartSubmit")
                            messageList = messageList.toMutableList().apply {
                                add("Uploading file ...")
                            }
                        }
                    }
                }
            }


        }
        ol {
            chartList?.forEach {
                li {
                    +it.name
                }
            } ?: Loading {}
        }
    }
}
