package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.madrona.njord.viewmodel.chartInstallViewModel
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.Label

@Composable
fun ChartInstaller() {
    val state = chartInstallViewModel.flow.collectAsState()
    Form {
        Div(attrs = {classes("mb-3")}) {
            Label(attrs = {
            }) {

            }
        }
    }
}