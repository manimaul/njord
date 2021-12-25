package io.madrona.njord.components

import io.madrona.njord.IconInfo
import io.madrona.njord.Theme
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

typealias IconData = Map<String, IconInfo>

suspend fun fetchThemeIcons(theme: Theme): IconData {
    val response = window
        .fetch("/v1/content/sprites/rastersymbols-${theme.name.lowercase()}.json")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}


val ControlSymbols = fc<Props> {
    var themeData: Map<Theme, IconData>? by useState(null)
    useEffectOnce {
        mainScope.launch {
            themeData = Theme.values().map {
                it to fetchThemeIcons(it)
            }.toMap()
        }
    }
    val theme = Theme.Day
    div {
        h2 {
            +"Chart Symbols"
        }
        div(classes = "col" ) {
            h4 {
                +theme.name
            }
            div(classes = "container") {
                themeData?.get(theme)?.asIterable()?.chunked(3)?.forEach {
                    div(classes = "row") {
                        it.forEach {
                            div(classes = "col-sm") {
                                br {  }
                                +it.key
                                img(src = "/v1/icon/day/${it.key}.png") {  }
                                br {  }
                            }
                        }
                    }
                }
            }
        }
    }
}