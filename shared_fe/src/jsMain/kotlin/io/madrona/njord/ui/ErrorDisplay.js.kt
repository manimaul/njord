package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import io.madrona.njord.viewmodel.utils.Fail
import org.jetbrains.compose.web.dom.*

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
    H1 {
        Text("Something went wrong")
    }
    P {
        B { Text(event.message) }
    }
    Button(attrs = {
        classes("btn", "btn-danger")
        onClick { function() }
    }) {
        Text("OK")
    }
}
