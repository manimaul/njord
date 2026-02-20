fun parsePqArray(arrayString: String): List<String?> {
    if (!arrayString.endsWith('}') || !arrayString.startsWith('{')) {
        return emptyList()
    }
    val arrayBody = arrayString.substring(1, arrayString.length - 1)
    if (arrayBody.isBlank()) {
        return emptyList()
    }

    val elements = mutableListOf<String?>()
    var inQuote = false
    var element = StringBuilder()
    for (i in arrayBody.indices) {
        val char = arrayBody[i]
        when (char) {
            '"' -> {
                if (inQuote && i > 0 && arrayBody[i - 1] == '\\') {
                    element.append(char)
                } else {
                    inQuote = !inQuote
                }
            }

            ',' -> {
                if (inQuote) {
                    element.append(char)
                } else {
                    elements.add(parseElement(element.toString()))
                    element = StringBuilder()
                }
            }

            else -> element.append(char)
        }
    }
    elements.add(parseElement(element.toString()))
    return elements
}

private fun parseElement(elementString: String): String? {
    if (elementString.trim().equals("null", true)) {
        return null
    } else {
        return elementString
    }
}