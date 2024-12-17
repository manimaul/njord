package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div

@Composable
actual fun AppBox(content: @Composable () -> Unit) {
    Div(
        attrs = {
            classes("container_fluid")
        }
    ) {
        content()
    }
}
