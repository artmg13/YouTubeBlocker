package com.example.youtubeblocker

object ReasonRules {
    const val DEFAULT_MINIMUM = 150
    const val MINIMUM = 50
    const val MAXIMUM = 5000
    const val MAX_TEXT = 10_000

    private fun space(codePoint: Int) = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    fun length(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            if (!space(point)) count++
            index += Character.charCount(point)
        }
        return count
    }

    fun valid(text: String, minimum: Int) =
        minimum in MINIMUM..MAXIMUM && length(text) >= minimum &&
            text.codePointCount(0, text.length) <= MAX_TEXT

    fun clean(text: String) = text.trim { it.isWhitespace() || Character.isSpaceChar(it) }
}
