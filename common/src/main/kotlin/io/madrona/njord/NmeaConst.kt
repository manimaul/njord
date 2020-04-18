package io.madrona.njord

object NmeaConst {
    // Messages have a maximum length of 82 characters, including the $ or ! starting and including the <CR><LF>
    // All transmitted data are printable ASCII characters between 0x20 (space) to 0x7e (~)
    // https://en.wikipedia.org/wiki/NMEA_0183
    const val maxNmeaLength = 82
    const val nmeaBeginDollarChar = '$'
    const val nmeaBeginDollar = nmeaBeginDollarChar.toByte() //0x/24
    const val nmeaBeginExclamChar = '!'
    const val nmeaBeginExclam = nmeaBeginExclamChar.toByte() // 0x21
    const val nmeaRangeStartChar = ' '
    const val nmeaRangeStart = nmeaRangeStartChar.toByte() // 0x20
    const val nmeaRangeEndChar = '~'
    const val nmeaRangeEnd = nmeaRangeEndChar.toByte() // 0x7e
    const val nmeaChecksumChar = '*'
}