package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import io.madrona.njord.viewmodel.utils.Fail

@Composable
actual fun LoadingSpinner() {
}

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
}
