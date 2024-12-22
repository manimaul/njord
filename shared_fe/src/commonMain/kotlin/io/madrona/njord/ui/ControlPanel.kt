package io.madrona.njord.ui

import androidx.compose.runtime.Composable

@Composable
expect fun ControlPanel(tab: String, path: String)
