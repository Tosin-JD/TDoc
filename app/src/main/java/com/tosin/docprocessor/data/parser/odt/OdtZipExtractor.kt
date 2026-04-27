package com.tosin.docprocessor.data.parser.odt

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class OdtZipExtractor {

    /**
     * Extracts all entries from the ODT ZIP file into a map for easy access.
     * This avoids reading the stream multiple times.
     */
    fun extractAllEntries(inputStream: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return entries
    }

    fun getEntry(entries: Map<String, ByteArray>, name: String): ByteArray? {
        return entries[name]
    }
}
