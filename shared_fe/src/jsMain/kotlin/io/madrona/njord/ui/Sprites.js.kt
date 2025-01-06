package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.complete
import io.madrona.njord.viewmodel.spriteViewModel
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text

@Composable
fun Sprites() {
    val state by spriteViewModel.flow.collectAsState()
    H2 { Text("Sprites") }
    Div(attrs = { classes("row") }) {
        state.sheet.complete(spriteViewModel) { sheet ->
            sheet.forEach {
                val sprite = it.key
                val info = it.value
                Div(attrs = { classes("col", "mb-3") }) {
                    Div(attrs = { classes("col-sm") }) {
                        Text("$sprite")
                        Br()
                        Div(attrs = {
                            style {
                                width(info.width.px)
                                height(info.height.px)
                                state.sheetUrl?.let {
                                    background("url(${it})")
                                    backgroundPosition("-${info.x}px -${info.y}px")
                                    display(DisplayStyle.InlineBlock)
                                    borderWidth(4.px)
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}