package com.tosin.docprocessor.data.parser.exception

import com.tosin.docprocessor.data.common.model.DocumentElement

data class ParseErrorContext(
    val fileName: String? = null,
    val bytesProcessed: Long? = null,
    val elementCount: Int = 0,
    val currentElement: String? = null,
    val documentHash: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val stack: List<String> = emptyList(),
    val extra: Map<String, String> = emptyMap()
)

class ParseException(
    message: String,
    val context: ParseErrorContext,
    cause: Throwable? = null
) : Exception(message, cause)
