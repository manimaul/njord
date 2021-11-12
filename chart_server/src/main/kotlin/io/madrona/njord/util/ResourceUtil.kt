package io.madrona.njord.util

fun Any.resourceAsString(name: String) : String? {
    return javaClass.classLoader.getResourceAsStream(name)?.let {
        it.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }
}

fun Any.resourceBytes(name: String) : ByteArray? {
    return javaClass.classLoader.getResourceAsStream(name)?.use { ips ->
        val byteList = mutableListOf<Byte>()
        val buffer = ByteArray(4096)
        var read = ips.read(buffer)
        while (read > -1) {
            for(i in 0 until read) {
                byteList.add(buffer[i])
            }
            read = ips.read(buffer)
        }
        byteList.toByteArray()
    }
}
