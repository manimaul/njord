package io.madrona.njord.util

import kotlin.random.Random

class UUID private constructor(val id: String){
    override fun equals(other: Any?): Boolean {
        return id.equals(other?.toString())
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return id
    }

    companion object {

        fun generateRandomString(length: Int): String {
            val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            return (1..length)
                .map { allowedChars.random(Random) }
                .joinToString("")
        }

        fun randomUUID(): UUID {
            return UUID("${generateRandomString(8)}-${generateRandomString(4)}-${generateRandomString(4)}-${ generateRandomString(4)}-${generateRandomString(8)}")
        }
    }
}