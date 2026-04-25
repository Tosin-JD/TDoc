package com.tosin.docprocessor.data.parser.core.models

data class TextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val fontSize: Float? = null
)