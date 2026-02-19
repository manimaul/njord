@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*

fun MemScope.allocStringArray(strings: List<String>) : CArrayPointer<CPointerVar<ByteVar>> {
    val nativeArray = allocArray<CPointerVar<ByteVar>>(strings.size + 1)
    strings.forEachIndexed { index, str ->
        nativeArray[index] = str.cstr.ptr
    }
    nativeArray[strings.size] = null
    return nativeArray
}

fun MemScope.allocStringArray(vararg string: String) : CArrayPointer<CPointerVar<ByteVar>> {
    val nativeArray = allocArray<CPointerVar<ByteVar>>(string.size + 1)
    string.forEachIndexed { index, str ->
        nativeArray[index] = str.cstr.ptr
    }
    nativeArray[string.size] = null
    return nativeArray
}

fun MemScope.allocIntArray(ints: List<Int>) : CArrayPointer<IntVar> {
    val nativeArray = allocArray<IntVar>(ints.size)
    ints.forEachIndexed { index, value ->
        nativeArray[index] = value
    }
    return nativeArray
}

fun MemScope.allocDoubleArray(doubles: List<Double>) : CArrayPointer<DoubleVar> {
    val nativeArray = allocArray<DoubleVar>(doubles.size)
    doubles.forEachIndexed { index, value ->
        nativeArray[index] = value
    }
    return nativeArray
}

fun MemScope.allocLongArray(longs: List<Long>) : CArrayPointer<LongVar> {
    val nativeArray = allocArray<LongVar>(longs.size)
    longs.forEachIndexed { index, value ->
        nativeArray[index] = value
    }
    return nativeArray
}
