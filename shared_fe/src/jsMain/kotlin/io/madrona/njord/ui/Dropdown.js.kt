package io.madrona.njord.ui

import androidx.compose.runtime.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.*

@Composable
fun <T> DropdownButton(
    selected: T,
    options: List<T>,
    callback: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun expand() = if (expanded) "show" else "hide"
    var filter by remember { mutableStateOf("") }
    Div(attrs = {
        classes("dropdown")
    }) {
        Button(attrs = {
            classes("btn", "btn-success", "dropdown-toggle")
            attr("aria-expanded", "$expanded")
            attr("data-bs-toggle", "dropdown")
            onClick {
                expanded = !expanded
            }
        }) {
            Text("$selected")
        }
        Div(attrs = {
            classes("dropdown-menu", expand())
        }) {
            Input(InputType.Text, attrs = {
                value(filter)
                classes("mx-3", "my-2", "form-control", "w-auto")
                placeholder("Type to filter...")
                onInput {
                    filter = it.value.trim()
                }
            })
            options.filter { "$it".contains(filter, true) }.forEach { option ->
                A(attrs = {
                    classes("dropdown-item")
                    onClick {
                        it.preventDefault()
                        callback(option)
                        expanded = false
                    }
                }, href = "#") {
                    Text("$option")
                }
            }
        }
    }
}
