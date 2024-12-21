package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.madrona.njord.js.Bootstrap
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Text

interface AccordionBuilder<T> {
    val item: T
    var title: String?
    var body: (@Composable () -> Unit)?
}

private var num = 0

@Composable
inline fun <reified A, reified B> Accordion(
    itemsA: List<A>,
    itemsB: List<B>,
    crossinline itemComponentA: (AccordionBuilder<A>) -> Unit,
    crossinline itemComponentB: (AccordionBuilder<B>) -> Unit,
) {
    Accordion(itemsA + itemsB) { builder ->
        if (builder.item is A) {
            itemComponentA(builder as AccordionBuilder<A>)
        } else {
            itemComponentB(builder as AccordionBuilder<B>)
        }

    }
}

@Composable
fun <T> Accordion(
    items: List<T>,
    itemComponent: (AccordionBuilder<T>) -> Unit,
) {
    val id = remember { "accordion-${++num}" }
    Div(attrs = {
        id(id)
        classes("accordion")
    }) {
        items.forEachIndexed { i, item ->
            val itemId = "item$i$id"
            val builder = object : AccordionBuilder<T> {
                override val item: T = item
                override var title: String? = null
                override var body: (@Composable () -> Unit)? = null
            }
            itemComponent(builder).takeIf { builder.title != null && builder.body != null }?.let {
                Div(attrs = { classes("accordion-item") }) {
                    H2(attrs = { classes("accordion-header") }) {
                        Button(attrs = {
                            classes("accordion-button", "collapsed")
                            onClick { Bootstrap.Collapse.getOrCreateInstance("#$itemId")?.toggle() }
                        }) {
                            Text(builder.title ?: "")
                        }
                    }
                    Div(attrs = {
                        id(itemId)
                        classes("accordion-collapse", "collapse")
                        attr("data-bs-parent", "#$id")
                    }) {
                        Div(attrs = {
                            classes("accordion-body")
                        }) {
                            builder.body?.invoke()
                        }
                    }
                }
            }
        }
    }
}