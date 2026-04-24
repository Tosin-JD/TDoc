package com.tosin.docprocessor.data.parser

import java.io.InputStream
import java.io.OutputStream

interface DocumentParser {
    fun parse(inputStream: InputStream): String
    fun save(outputStream: OutputStream, content: String)
}
