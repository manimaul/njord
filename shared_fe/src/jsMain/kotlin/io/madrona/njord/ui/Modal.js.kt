package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.remember
import io.madrona.njord.js.Bootstrap
import io.madrona.njord.viewmodel.BaseViewModel
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

data class ModalState(
    val shown: Boolean = false,
    val modal: Bootstrap.Modal? = null
)

val modalViewModel = ModalViewModel()

class ModalViewModel : BaseViewModel<ModalState>(ModalState())  {
    override fun reload() { }

    fun modalCreated(id: String) {
        document.querySelector("#$id")?.let { element ->
            element.addEventListener("hidden.bs.modal", EventListener {
                setState { copy(shown = false) }
            })
            element.addEventListener("shown.bs.modal", EventListener {
                setState { copy(shown = true) }
            })
            Bootstrap.Modal.getOrCreateInstance(element)?.let { modal ->
                setState { copy(modal = modal) }
            }
        }
    }

    fun modalDestroyed() {
        setState {
            ModalState()
        }
    }

    fun show() {
        withState { if (!it.shown) it.modal?.show() }
    }

    fun hide() {
        withState { if (it.shown) it.modal?.hide() }
    }
}

@Composable
fun Modal(
    id: String = "modal-${++num}",
    title: String,
    onClose: () -> Unit,
    content: ContentBuilder<HTMLDivElement>,
) {
    val id = remember { "modal-${++num}" }
    Div(attrs = {
        id(id)
        classes("modal", "fade")
        attr("tabindex", "-1")
        attr("aria-labelledby", "$id-title")
        attr("aria-hidden", "true")
        ref {
            modalViewModel.modalCreated(id)
            object : DisposableEffectResult {
                override fun dispose() {
                    modalViewModel.modalDestroyed()
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
