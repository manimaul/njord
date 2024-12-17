package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.madrona.njord.viewmodel.routeViewModel
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
actual fun NotFound() {
    val state by routeViewModel.flow.collectAsState()
    Div {
        Text("Whoops ")
        B { Text(state.current.path) }
        Text(" is not a known location!")
    }
}
