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
                +"Njord Marine"
            }
            h3 {
                +"Electronic Navigational Chart (ENC) Server"
            }
            h6 {
                +"Njord version = ${js("NJORD_VERSION")}"
            }
            h6 {
                +"Gdal version = ${aboutJson?.gdalVersion ?: "..."}"
            }
            a(href = "https://github.com/manimaul/njord", target = "_blank") {
                +"https://github.com/manimaul/njord"
            }
        }
    }
}