package com.tosin.docprocessor.data.parser

import androidx.compose.ui.text.AnnotatedString
import java.io.InputStream
import java.io.OutputStream

interface DocumentParser {
    fun parse(inputStream: InputStream): AnnotatedString
    fun save(outputStream: OutputStream, content: AnnotatedString)
}
