@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.set

fun MemScope.allocStringArray(vararg string: String) : CArrayPointer<CPointerVar<ByteVar>> {
    val nativeArray = allocArray<CPointerVar<ByteVar>>(string.size + 1)
    string.forEachIndexed { index, str ->
        nativeArray[index] = str.cstr.getPointer(this)
    }
    nativeArray[string.size] = null
    return nativeArray
}

fun MemScope.allocIntArray(vararg int: Int) : CArrayPointer<IntVar> {
    val nativeArray = allocArray<IntVar>(int.size)
    int.forEachIndexed { index, value ->
        nativeArray[index] = value
    }
    return nativeArray
}

fun MemScope.allocDoubleArray(vararg double: Double) : CArrayPointer<DoubleVar> {
    val nativeArray = allocArray<DoubleVar>(double.size)
    double.forEachIndexed { index, value ->
        nativeArray[index] = value
    }
    return nativeArray
}
