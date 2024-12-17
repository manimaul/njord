package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div

@Composable
actual fun RouteContent(content: @Composable () -> Unit) {
    Div(
        attrs = {
            classes("m-2")
        }
    ) {
        content()
    }
}
