package io.madrona.njord.components

import io.madrona.njord.AboutJson
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.css.paddingTop
import kotlinx.css.px
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.Props
import react.dom.*
import react.fc
import react.useEffectOnce
import react.useState
import styled.css
import styled.styledDiv

suspend fun fetchAbout(): AboutJson {
    val response = window
        .fetch("/v1/about/version")
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

val mainScope = MainScope()

val About = fc<Props> {
    var aboutJson: AboutJson? by useState(null)
    useEffectOnce {
        mainScope.launch {
            aboutJson = fetchAbout()
        }
    }
    div(classes = "container") {
        styledDiv {
            css {
                paddingTop = 20.px
            }
            h1 {
                +"Njord S57 Chart Server"
            }
            h6 {
                +"Njord ${js("NJORD_VERSION")}"
            }
            h6 {
                +"Gdal ${aboutJson?.gdalVersion ?: "..."}"
            }
        }
    }
}