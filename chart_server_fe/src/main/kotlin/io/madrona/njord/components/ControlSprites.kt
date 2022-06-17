package io.madrona.njord.components

import io.madrona.njord.IconInfo
import io.madrona.njord.pathToFullUrl
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.css.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.*
import react.dom.*
import styled.css
import styled.styledImg

typealias IconData = Map<String, IconInfo>

fun spriteJsonUrl() = "/v1/content/sprites/simplified.json".pathToFullUrl()

fun spritePngUrl() = "/v1/content/sprites/simplified.png".pathToFullUrl()

suspend fun fetchThemeIcons(): IconData {
    val response = window
        .fetch(spriteJsonUrl())
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

val ControlSprites = fc<Props> {
    var themeData: IconData? by useState(null)
    useEffectOnce {
        mainScope.launch {
            themeData = fetchThemeIcons()
        }
    }

    div {
        h2 {
            +"Chart Symbol Sprites"
        }
        div {
            +"Sprite sheet: "
            a(href = spritePngUrl(), target = "_blank") {
                +spritePngUrl()
            }
        }
        div {
            +"Sprite json: "
            a(href = spriteJsonUrl(), target = "_blank") {
                +spriteJsonUrl()
            }
        }
        themeData?.let {
            div(classes = "col") {
                div(classes = "container") {
                    div(classes = "row") {
                        it.forEach {
                            val imgData = it.value
                            div(classes = "col-sm") {
                                br { }
                                +it.key
                                a(href= "/v1/icon/${it.key}.png") {
                                    styledImg {
                                        css {
                                            width = imgData.width.px
                                            height = imgData.height.px
                                            background = "url('${spritePngUrl()}');"
                                            backgroundPosition = "-${imgData.x}px -${imgData.y}px"
                                            display = Display.inlineBlock
                                            borderWidth = 4.px
                                        }
                                    }
                                }
                                br { }
                            }
                        }
                    }
                }
            }
        } ?: Loading {}
    }
}
