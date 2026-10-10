package com.tosin.docprocessor.data.parser.odt

import java.io.InputStream
import com.tosin.docprocessor.data.parser.util.DefaultZipExtractor

class OdtZipExtractor {

    /**
     * Extracts all entries from the ODT ZIP file into a map for easy access.
     * This avoids reading the stream multiple times.
     */
    fun extractAllEntries(inputStream: InputStream): Map<String, ByteArray> {
        return DefaultZipExtractor().extract(inputStream)
    }

    fun getEntry(entries: Map<String, ByteArray>, name: String): ByteArray? {
        return entries[name]
    }
}
