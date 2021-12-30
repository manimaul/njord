package io.madrona.njord.components

import kotlinx.css.*
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.*
import react.router.dom.Link
import styled.css
import styled.styledDiv

fun RBuilder.dropdown(
    items: Iterable<DropdownItem>,
    label: String,
    enableFilter: Boolean = false,
    callback: ((DropdownLink?) -> Unit)? = null
) {
    Dropdown {
        attrs.also {
            it.items = items
            it.label = label
            it.enableFilter = enableFilter
            it.callback = callback
        }
    }
}

interface DropdownProps : Props {
    var items: Iterable<DropdownItem>
    var label: String
    var enableFilter: Boolean
    var callback: ((DropdownLink) -> Unit)?
}

sealed class DropdownItem
data class DropdownLink(
    val label: String,
    val href: String? = null,
    val active: Boolean = false
) : DropdownItem()

object DropdownDivider : DropdownItem()

fun Boolean.show() : String {
    return if (this) {
        "show"
    } else {
        ""
    }
}

val Dropdown = fc<DropdownProps> { props ->
    var filter: String by useState("")
    var expanded: Boolean by useState(false)
    div(classes = "dropdown") {
        button(classes = "btn btn-secondary dropdown-toggle ${expanded.show()}") {
            setProp("type", "button")
            setProp("data-bs-toggle", "dropdown")
            setProp("aria-expanded", expanded)
            setProp("data-bs-auto-close", false)
            +props.label
            attrs.also {
                it.onClick = {
                    if (!expanded) {
                        expanded = true
                    }
                }
            }
        }
        ul(classes = "dropdown-menu dropdown-menu-dark ${expanded.show()}") {
            if (props.enableFilter) {
                styledDiv {
                    css {
                        paddingLeft = 0.375.rem
                        paddingRight = 0.375.rem
                    }
                    attrs.also {
                        it.classes = setOf("input-group", "mb-2")
                    }
                    input(type = InputType.text, classes = "form-control") {
                        setProp("placeholder", "Filter")
                        attrs.also { input ->
                            input.onChangeFunction = { event ->
                                (event.target as? HTMLInputElement)?.let {
                                    filter = it.value
                                }
                            }
                            input.value = filter
                        }
                    }
                    button(type = ButtonType.button, classes = "btn btn-success") {
                        +"Clear"
                        attrs.also { button ->
                            button.onClick = {
                                filter = ""
                            }
                        }
                    }
                }
                hr(classes = "dropdown-divider") {}
            }
            props.items.filter { item ->
                if (item is DropdownLink) {
                    filter.takeIf {
                        it.isNotBlank()
                    }?.let {
                        console.log("label = ${item.label}")
                        console.log("filter = $it")
                        item.label.contains(it, true)
                    } ?: true
                } else {
                    true
                }
            }.forEach { item ->
                li {
                    when (item) {
                        is DropdownLink -> {
                            item.href?.let {
                                Link {
                                    +item.label
                                    attrs.also {
                                        it.to = item.href
                                        it.className = "dropdown-item"
                                        it.onClick = {
                                            props.callback?.invoke(item)
                                            expanded = false
                                        }
                                    }
                                }
                            } ?: span(classes = "dropdown-item") {
                                +item.label
                                attrs.also {
                                    it.onClick = {
                                        props.callback?.invoke(item)
                                        expanded = false
                                    }
                                }
                            }
                        }
                        DropdownDivider -> hr(classes = "dropdown-divider") {}
                    }
                }
            }
        }
    }
}
