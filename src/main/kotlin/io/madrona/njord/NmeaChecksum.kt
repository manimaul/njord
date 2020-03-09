package io.madrona.njord

import javax.inject.Inject

class NmeaChecksum @Inject constructor() {
    fun isValid(line: String?): Boolean {
        line?.takeIf {
            it.isNotBlank()
        }?.let {
            val start = line.indexOf('$')
            val end = line.indexOf('*')
            if (start == 0 && end > 0) {
                val payload = line.substring(start + 1, end)
                val checkSum = line.substring(end + 1)
                var sum = 0
                for (element in payload) {
                    sum = sum xor element.toInt()
                }
                val hexSum = Integer.toHexString(sum).toUpperCase()
                return checkSum == hexSum
            }
        }
        return false
    }
}