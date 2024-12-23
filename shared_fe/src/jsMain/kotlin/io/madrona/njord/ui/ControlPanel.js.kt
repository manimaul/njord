package io.madrona.njord.ui

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*

@Composable
actual fun ControlPanel(tab: String, path: String) {
    Div(attrs = {
        classes("container", "Content")
    }) {
        H1 { Text("Control Panel") }
        Tabs(
            basePath = "control",
            activeTabKey = tab,
            tabs = listOf(
                TabData(
                    tabKey = "charts_catalog",
                    title = "Chart Catalog",
                    selectionContent = { ChartCatalog() }
                ),
                TabData(
                    tabKey = "charts_installer",
                    title = "Chart Installer",
                    selectionContent = { H1 { Text("hello installer") } }
                ),
                TabData(
                    tabKey = "symbols",
                    title = "Symbols",
                    selectionContent = { H1 { Text("hello symbols") } }
                ),
                TabData(
                    tabKey = "sprites",
                    title = "Sprites",
                    selectionContent = { H1 { Text("hello sprites") } }
                ),
                TabData(
                    tabKey = "colors",
                    title = "Colors",
                    selectionContent = { H1 { Text("hello colors") } }
                ),
                TabData(
                    tabKey = "endpoints",
                    title = "Endpoints",
                    selectionContent = { H1 { Text("hello endpoints") } }
                ),
            )
        )
    }
}
