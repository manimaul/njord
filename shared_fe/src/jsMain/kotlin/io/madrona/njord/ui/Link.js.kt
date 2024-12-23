package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import io.madrona.njord.viewmodel.routeViewModel
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Text

@Composable
fun Link(
    label: String,
    path: String,
) {
    A(
        attrs = {
            onClick {
                it.preventDefault()
                routeViewModel.pushRoute(path)
                modalViewModel.hide()
            }
        },
        href = path
    ) {
        Text(label)
    }
}