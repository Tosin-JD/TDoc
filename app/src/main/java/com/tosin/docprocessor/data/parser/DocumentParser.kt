package com.tosin.docprocessor.data.parser

import com.tosin.docprocessor.data.model.DocumentElement
import java.io.InputStream
import java.io.OutputStream

interface DocumentParser {
    fun parse(inputStream: InputStream): List<DocumentElement>
    fun save(outputStream: OutputStream, content: List<DocumentElement>)
}
