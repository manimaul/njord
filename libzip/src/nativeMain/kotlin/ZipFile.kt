class ZipFile(file: File) {
    fun size(): Long {
        TODO()
    }

    fun entries() :List<ZipFileEntry> {
        TODO()
    }
}

class ZipFileEntry() {

    fun name() : String {
        TODO()
    }

    fun readFileChunked(chunkSize: Int = 8192, block: (ByteArray, Int) -> Unit) {
        TODO()
    }

    fun unzipToPath(path: String) {
        TODO()
    }

}