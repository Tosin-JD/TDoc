package com.tosin.docprocessor.data.parser.util

import java.io.InputStream
import java.util.zip.ZipInputStream

interface ZipExtractor {
    fun extract(input: InputStream): Map<String, ByteArray>
}

class DefaultZipExtractor : ZipExtractor {
    override fun extract(input: InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return result
    }
}
