package com.tosin.docprocessor.data.parser.exception

import com.tosin.docprocessor.data.common.model.DocumentElement

data class ParseErrorContext(
    val fileName: String? = null,
    val bytesParsed: Long? = null,
    val currentElement: String? = null,
    val documentHash: String? = null,
    val extra: Map<String, String> = emptyMap()
)

class ParseException(
    message: String,
    val context: ParseErrorContext,
    cause: Throwable? = null
) : Exception(message, cause)
