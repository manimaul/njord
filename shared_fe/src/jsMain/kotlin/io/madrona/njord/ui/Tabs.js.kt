package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.viewmodel.routeViewModel
import org.jetbrains.compose.web.dom.*

data class TabData(
    val tabKey: String,
    val subPath: String? = null,
    val title: String,
    val selectionContent: @Composable (String?) -> Unit
)

@Composable
fun Tabs(
    basePath: String,
    activeTabKey: String,
    tabs: List<TabData>
) {
    val tab = tabs.find { it.tabKey == activeTabKey }
    Ul(attrs = {
        classes("nav", "nav-tabs")
    }) {
        tabs.forEach { ea ->
            val isActive = ea.tabKey == activeTabKey
            Li(attrs = {
                classes("nav-item")
            }) {
                Button(attrs = {
                    if (isActive) {
                        classes("nav-link", "active")
                        attr("aria-current", "page")
                    } else {
                        classes("nav-link")
                    }
                    onClick {
                        val path = ea.subPath?.let { "/$basePath/${ea.tabKey}/${it}" } ?: "/$basePath/${ea.tabKey}"
                        routeViewModel.pushRoute(path)
                    }
                }) {
                    Text(ea.title)
                }
            }
        }
    }
    tab?.let {
        Div(attrs = {
            classes("mt-3")
        }) {
            it.selectionContent(it.subPath)
        }
    }
}

