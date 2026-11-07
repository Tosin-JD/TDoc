package com.tosin.docprocessor.data.writer.common

internal object XmlTextSanitizer {
    fun sanitize(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                    append(char)
                    append(value[index + 1])
                    index++
                }
                char.isSurrogate() -> append('\uFFFD')
                char == '\u0009' || char == '\u000A' || char == '\u000D' ||
                    char.code in 0x20..0xD7FF || char.code in 0xE000..0xFFFD -> append(char)
                else -> append('\uFFFD')
            }
            index++
        }
    }

    fun escapeText(value: String): String = sanitize(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun escapeAttribute(value: String): String = escapeText(value)
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
