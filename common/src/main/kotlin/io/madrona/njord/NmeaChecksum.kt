package io.madrona.njord

import java.lang.Integer.toHexString
import javax.inject.Inject

class NmeaChecksum @Inject constructor() {
    fun isValid(line: String?): Boolean {
        line?.takeIf {
            it.isNotBlank() && it.length in 1..82 && (line[0] == '$' || line[0] == '!')
        }?.let {
            val end = line.indexOf('*')
            if (end > 0) {
                val payload = line.substring(1, end)
                val checkSum = line.substring(end + 1)
                var sum = 0
                for (element in payload) {
                    sum = sum xor element.toInt()
                }
                val hexSum = sum.toNmeaHex()
                return checkSum == hexSum
            }
        }
        return false
    }
}

private fun Int.toNmeaHex() : String {
    val sum = toHexString(this).toUpperCase()
    return if (sum.length == 1) {
        "0$sum"
    } else {
        sum
    }
}