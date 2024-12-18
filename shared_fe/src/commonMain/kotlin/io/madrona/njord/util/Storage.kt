package io.madrona.njord.util

expect inline fun <reified T> localStoreSet(item: T?)
expect inline fun <reified T> localStoreGet(): T?
