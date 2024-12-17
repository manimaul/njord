package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
actual fun Home() {
    Div {
        Text("Hello World")
    }
}
