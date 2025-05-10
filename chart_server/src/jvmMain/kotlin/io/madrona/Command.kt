package io.madrona

internal object Command {
    /**
     * MoveTo: 1. (2 parameters follow)
     */
    const val MoveTo: Int = 1

    /**
     * LineTo: 2. (2 parameters follow)
     */
    const val LineTo: Int = 2

    /**
     * ClosePath: 7. (no parameters follow)
     */
    const val ClosePath: Int = 7
}