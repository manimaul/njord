package io.madrona.njord.viewmodel.utils


sealed class Async<out T>(
    val complete: Boolean,
    open val value: T?,
    open val error: List<Throwable>? = null,
) {

    fun loading(): Loading<T> {
        return Loading(value)
    }

    fun error(throwable: Throwable? = null): Fail<T> {
        return Fail.from(throwable, value = value)
    }

    override fun toString(): String {
        val prefix = when (this) {
            is Complete -> "Complete"
            is Fail -> "Error"
            is Loading -> "Loading"
            Uninitialized -> "Uninitialized"
        }
        return "$prefix:Async(complete=$complete, value=$value)"
    }
}

suspend fun <T, R> Async<T>.flatMap(handler: suspend (T) -> Async<R>) : Async<R> {
    return value?.let { handler(it) } ?: Fail()
}

suspend fun <T, R> Async<T>.map(handler: suspend (T) -> R) : Async<R> {
    return value?.let { Complete(handler(it)) } ?: Fail()
}

suspend fun <T> Async<T>.andThen(handler: suspend (T) -> Unit) : Async<T> {
    value?.let { Complete(handler(it)) }
    return this
}

suspend fun <T, R> Async<T>.mapNotNull(handler: suspend (T) -> R?) : Async<R> {
    return value?.let { handler(it)?.let { Complete(it) } } ?: Fail()
}

fun <T> Async<T>.mapErrorMessage(handler: (Fail<T>) -> String) : Async<T> {
    return if (this is Fail) {
        Fail(value, error, handler(this))
    } else {
        this
    }
}

private const val unauthorized: Short = 401
fun <T> NetworkResponse<T>.toAsync(previous: Async<T>? = null): Async<T> {
    return if (ok) {
        body?.let {
            Complete(it)
        } ?: Fail.from(error, value = previous?.value, message = "$status - Network status")
    } else if (status == unauthorized) {
        Fail.from(error, value = previous?.value, message = "401 - Unauthorized")
    } else {
        Fail.from(error, value = previous?.value, message = "$status - Network status")
    }
}

object Uninitialized : Async<Nothing>(false, null)
data class Loading<out T>(override val value: T? = null) : Async<T>(false, value)
data class Complete<out T>(override val value: T) : Async<T>(true, value)

const val defaultErrorMessage = "An unknown error occurred :("
data class Fail<out T>(
    override val value: T? = null,
    override val error: List<Throwable> = emptyList(),
    val message: String = defaultErrorMessage,
) : Async<T>(true, value) {
    companion object {
        fun <T> from(vararg errors: Throwable?,  value: T? = null, message: String = defaultErrorMessage) : Fail<T> {
            val error = errors.filterNotNull()
            return Fail(value, error, message)
        }
        fun <T> from(vararg errors: List<Throwable>?,  value: T? = null, message: String = defaultErrorMessage) : Fail<T> {
            val error = errors.filterNotNull().flatten()
            return Fail(value, error, message)
        }
    }
}

suspend fun <T, R, O> combine(
    one: NetworkResponse<T>,
    two: NetworkResponse<R>,
    reducer: suspend (T, R) -> O
): O? {
    val oneAsync = one.toAsync()
    val twoAsync = two.toAsync()
    return if (oneAsync is Complete && twoAsync is Complete) {
        reducer(oneAsync.value, twoAsync.value)
    } else {
        null
    }
}

suspend fun <A, B, R> combineAsync(
    one: NetworkResponse<A>,
    two: NetworkResponse<B>,
    reducer: suspend (A, B) -> R
): Async<R> {
    val oneAsync = one.toAsync()
    val twoAsync = two.toAsync()
    return if (oneAsync is Complete && twoAsync is Complete) {
        Complete(reducer(oneAsync.value, twoAsync.value))
    } else {
        Fail.from(oneAsync.error, twoAsync.error)
    }
}

suspend fun <A, B, C, R> combineAsync(
    oneAsync: Async<A>,
    twoAsync: Async<B>,
    threeAsync: Async<C>,
    reducer: suspend (A, B, C) -> R
): Async<R> {
    return if (oneAsync is Complete && twoAsync is Complete && threeAsync is Complete) {
        Complete(reducer(oneAsync.value, twoAsync.value, threeAsync.value))
    } else {
        Fail.from(oneAsync.error, twoAsync.error, threeAsync.error)
    }
}

suspend fun <A, B, C, R> combineAsync(
    one: NetworkResponse<A>,
    two: NetworkResponse<B>,
    three: NetworkResponse<C>,
    reducer: suspend (A, B, C) -> R
): Async<R> {
    val oneAsync = one.toAsync()
    val twoAsync = two.toAsync()
    val threeAsync = three.toAsync()
    return if (oneAsync is Complete && twoAsync is Complete && threeAsync is Complete) {
        Complete(reducer(oneAsync.value, twoAsync.value, threeAsync.value))
    } else {
        Fail.from(oneAsync.error, twoAsync.error, threeAsync.error)
    }
}

suspend fun <A, B, C, D, R> combineAsync(
    one: NetworkResponse<A>,
    two: NetworkResponse<B>,
    three: NetworkResponse<C>,
    four: NetworkResponse<D>,
    reducer: suspend (A, B, C, D) -> R
): Async<R> {
    val oneAsync = one.toAsync()
    val twoAsync = two.toAsync()
    val threeAsync = three.toAsync()
    val fourAsync = four.toAsync()
    return if (oneAsync is Complete && twoAsync is Complete && threeAsync is Complete && fourAsync is Complete) {
        Complete(reducer(oneAsync.value, twoAsync.value, threeAsync.value, fourAsync.value))
    } else {
        Fail.from(oneAsync.error, twoAsync.error, threeAsync.error, fourAsync.error)
    }
}
