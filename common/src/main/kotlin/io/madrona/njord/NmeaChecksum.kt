package io.madrona.njord

import io.madrona.njord.NmeaConst.nmeaBeginDollarChar
import io.madrona.njord.NmeaConst.nmeaBeginExclamChar
import io.madrona.njord.NmeaConst.nmeaChecksumChar
import io.madrona.njord.NmeaConst.nmeaRangeEndChar
import io.madrona.njord.NmeaConst.nmeaRangeStartChar
import java.lang.Integer.toHexString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NmeaChecksum @Inject constructor() {

    fun createVendorMessage(message: String, filter: Boolean = false): String? {
        val builder = StringBuilder("\$PNJM,")
        message.forEach {
            if (it in nmeaRangeStartChar..nmeaRangeEndChar) {
                builder.append(it)
            } else if (!filter) {
                return null
            }
        }
        val sum = makeChecksum(builder, 1)
        builder.append(nmeaChecksumChar).append(sum)
        return builder.toString()
    }

    fun isValid(line: String?): Boolean {
        line?.takeIf {
            it.isNotBlank() &&
                    it.length in 1..82 &&
                    (line[0] == nmeaBeginDollarChar ||
                            line[0] == nmeaBeginExclamChar)
        }?.let {
            val end = line.indexOf(nmeaChecksumChar)
            if (end > 0) {
                val checkSum = line.substring(end + 1)
                val hexSum = makeChecksum(line.substring(1, end))
                return checkSum == hexSum
            }
        }
        return false
    }

    private fun makeChecksum(payload: CharSequence, start: Int = 0): String {
        var sum = 0
        payload.forEachIndexed { i, c ->
            if (i >= start) {
                sum = sum xor c.toInt()
            }
        }
        return sum.toNmeaHex()
    }
}

private fun Int.toNmeaHex(): String {
    val sum = toHexString(this).toUpperCase()
    return if (sum.length == 1) {
        "0$sum"
    } else {
        sum
    }
}