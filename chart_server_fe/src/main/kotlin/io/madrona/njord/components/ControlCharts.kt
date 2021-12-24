package io.madrona.njord.components

import io.madrona.njord.ChartItem
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.Props
import react.dom.*
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

val ControlCharts = fc<Props> {
    var chartList: List<ChartItem> by useState(emptyList())
    useEffectOnce {
        mainScope.launch {
            chartList = fetchCharts()
        }
    }
    div {
        h2 {
            +"Installed Charts"
        }
        ol {
            chartList.forEach {
                li {
                    +it.name
                }
            }
        }
    }
}