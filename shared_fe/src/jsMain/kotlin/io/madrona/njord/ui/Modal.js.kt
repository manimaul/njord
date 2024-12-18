package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.remember
import io.madrona.njord.js.Bootstrap
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.EventListener


private var num = 0

@Composable
fun Modal(
    title: String,
    onClose: () -> Unit,
    showHideFlow: Flow<Boolean>,
    content: ContentBuilder<HTMLDivElement>,
) {
    val scope = CoroutineScope(Dispatchers.Default)
    val id = remember { "modal-${++num}" }
    Div(attrs = {
        id(id)
        classes("modal", "fade")
        attr("tabindex", "-1")
        attr("aria-labelledby", "$id-title")
        attr("aria-hidden", "true")
        ref {
            document.querySelector("#$id")?.let { element ->
                println("bootstrap modal id $id $element")
                var num = 0
                element.addEventListener("hidden.bs.modal", EventListener {
                    println("bootstrap modal id $id hidden ${++num}")
                    onClose()
                })
                println("finding bootstrap modal id $id instance")
                Bootstrap.Modal.getOrCreateInstance(element)?.let { modal ->
                    println("bootstrap modal id $id instance $modal")
                    scope.launch {
                        showHideFlow.collect {
                            if (it) {
                                modal.show()
                            } else {
                                modal.hide()
                            }
                        }
                    }
                }
            }
            object : DisposableEffectResult {
                override fun dispose() {
                    println("bootstrap modal id $id disposed")
                    scope.cancel()
                }
            }
        }
    }) {
        Div(attrs = {
            classes("modal-dialog")
        }) {
            Div(attrs = {
                classes("modal-content")
            }) {
                Div(attrs = {
                    classes("modal-header")
                }) {
                    H1(attrs = {
                        classes("modal-title", "fs-5")
                        id("$id-title")
                    }) { Text(title) }
                    Button(attrs = {
                        type(ButtonType.Button)
                        classes("btn-close")
                        attr("data-bs-dismiss", "modal")
                        attr("aria-label", "Close")
                        onClick { onClose() }
                    })
                }
                Div(attrs = {
                    classes("modal-body")
                }, content)
            }
        }
    }
}
