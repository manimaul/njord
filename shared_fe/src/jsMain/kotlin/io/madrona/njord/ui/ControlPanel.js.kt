package io.madrona.njord.ui

import androidx.compose.runtime.*
import io.madrona.njord.viewmodel.symbolViewModel
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
                    selectionContent = { ChartInstaller() }
                ),
                TabData(
                    tabKey = "symbols",
                    title = "Symbols",
                    subPath = "ADMARE",
                    selectionContent = { Symbols() }
                ),
                TabData(
                    tabKey = "sprites",
                    title = "Sprites",
                    selectionContent = { Sprites() }
                ),
                TabData(
                    tabKey = "colors",
                    title = "Colors",
                    selectionContent = { Colors() }
                ),
            )
        )
    }
}
