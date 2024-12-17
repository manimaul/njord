package io.madrona.njord.ui

import androidx.compose.runtime.Composable
import io.madrona.njord.viewmodel.utils.Fail

@Composable
expect fun NotFound()

@Composable
expect fun Home()

@Composable
expect fun AppBox(content: @Composable () -> Unit)

@Composable
expect fun RouteContent(content: @Composable () -> Unit)

@Composable
expect fun NavBar()

@Composable
expect fun LoadingSpinner()

@Composable
expect fun <A> ErrorDisplay(event: Fail<A>, function: () -> Unit)

@Composable
expect fun ChartView()
