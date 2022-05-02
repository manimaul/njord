package io.madrona.njord.components

import io.madrona.njord.IconInfo
import io.madrona.njord.Theme
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

fun spriteJsonUrl(theme: Theme) = "/v1/content/sprites/rastersymbols-${theme.name.lowercase()}.json".pathToFullUrl()

fun spritePngUrl(theme: Theme) = "/v1/content/sprites/rastersymbols-${theme.name.lowercase()}.png".pathToFullUrl()

suspend fun fetchThemeIcons(theme: Theme): IconData {
    val response = window
        .fetch(spriteJsonUrl(theme))
        .await()
        .text()
        .await()
    return Json.decodeFromString(response)
}

val ControlSprites = fc<Props> {
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
            +"Chart Symbol Sprites - ${theme.name}"
        }
        div {
            +"Sprite sheet: "
            a(href = spritePngUrl(theme), target = "_blank") {
                +spritePngUrl(theme)
            }
        }
        div {
            +"Sprite json: "
            a(href = spriteJsonUrl(theme), target = "_blank") {
                +spriteJsonUrl(theme)
            }
        }
        themeData?.let {
            div(classes = "col") {
                div(classes = "container") {
                    themeData?.get(theme)?.asIterable()?.chunked(3)?.forEach {
                        div(classes = "row") {
                            it.forEach {
                                val imgData = it.value
                                div(classes = "col-sm") {
                                    br { }
                                    +it.key
                                    styledImg {
                                        css {
                                            width = imgData.width.px
                                            height = imgData.height.px
                                            background = "url('${spritePngUrl(theme)}');"
                                            backgroundPosition = "-${imgData.x}px -${imgData.y}px"
                                            display = Display.inlineBlock
                                            borderWidth = 4.px
                                        }
                                    }
                                    br { }
                                }
                            }
                        }
                    }
                }
            }
        } ?: Loading {}
    }
}