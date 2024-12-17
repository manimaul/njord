package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import io.madrona.njord.viewmodel.utils.Fail

@Composable
actual fun NotFound() {
}

@Composable
actual fun Home() {
}

@Composable
actual fun AppBox(content: @Composable () -> Unit) {
}

@Composable
actual fun NavBar() {
}

@Composable
actual fun LoadingSpinner() {
}

@Composable
actual fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit) {
}

@Composable
actual fun RouteContent(content: @Composable () -> Unit) {
}