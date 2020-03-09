package io.madrona.njord

object SerialTest {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        NmeaSource("/dev/ttyUSB0", 38400, NmeaChecksum())
                .output()
                .subscribe { data: String? -> println(data) }
        val t = Thread(
                Runnable {
                    try {
                        Thread.sleep(1000000)
                    } catch (ie: InterruptedException) {
                    }
                })
        t.start()
        println("Started")
    }
}