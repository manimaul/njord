package io.madrona.njord.viewmodel

import androidx.compose.runtime.Composable
import io.madrona.njord.ui.ErrorDisplay
import io.madrona.njord.ui.LoadingSpinner
import io.madrona.njord.viewmodel.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<T>(
    initialState: T,
) : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val internalState = MutableStateFlow(initialState)
    val flow: StateFlow<T>
        get() = internalState

    protected fun <A> withState(handler: (T) -> A): A {
        return handler(internalState.value)
    }

    protected suspend fun <A> withStateAsync(handler: suspend (T) -> A): A {
        return handler(internalState.value)
    }

    protected fun <A> MutableStateFlow<A>.setState(reducer: suspend A.() -> A) {
        launch {
            value = reducer(value)
        }
    }

    protected fun setState(reducer: suspend T.() -> T) {
        launch {
            internalState.value = reducer(internalState.value)
        }
    }

    protected fun <A, B> setState(
        n1: suspend () -> Async<A>,
        n2: suspend () -> Async<B>,
        reducer: suspend T.(Async<A>, Async<B>) -> T
    ) {
        launch {
            internalState.value = reducer(internalState.value, n1(), n2())
        }
    }

    protected fun <A> setState(
        n: suspend () -> Async<A>,
        reducer: suspend T.(Async<A>) -> T
    ) {
        launch {
            internalState.value = reducer(internalState.value, n())
        }
    }

    abstract fun reload()

}

@Composable
fun <A, B> asyncComplete(
    viewModel: BaseViewModel<*>,
    one: Async<A>,
    two: Async<B>,
    complete: @Composable (A, B) -> Unit
) {
    if (one is Complete && two is Complete) {
        complete(one.value, two.value)
    } else if (one is Loading || two is Loading) {
        LoadingSpinner()
    } else if (one is Fail) {
        ErrorDisplay(one) { viewModel.reload() }
    } else if (two is Fail) {
        ErrorDisplay(two) { viewModel.reload() }
    }
}

@Composable
fun <A> Async<A>.complete(viewModel: BaseViewModel<*>, handler: @Composable (A) -> Unit) =
    this.complete(viewModel, { LoadingSpinner() }, handler)

@Composable
fun <A> Async<A>.complete(
    viewModel: BaseViewModel<*>,
    loading: @Composable () -> Unit,
    complete: @Composable (A) -> Unit
) {
    when (val event = this) {
        is Complete -> complete(event.value)
        is Fail -> ErrorDisplay(event) { viewModel.reload() }
        is Loading -> loading()
        Uninitialized -> Unit
    }
}

