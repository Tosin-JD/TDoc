package com.tosin.docprocessor.data.parser.util

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

interface ZipExtractor {
    fun extract(input: InputStream): Map<String, ByteArray>
}

/**
 * Bounded ZIP reader shared by the OOXML and ODF importers.
 *
 * Neither format needs path extraction, so entries are kept in memory.  The
 * limits prevent an untrusted office document from exhausting the process with
 * a ZIP bomb before XML parsing starts.
 */
class DefaultZipExtractor(
    private val limits: ZipLimits = ZipLimits()
) : ZipExtractor {
    override fun extract(input: InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entryCount = 0
            var totalBytes = 0L
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    requireSafeEntryName(entry.name)
                    entryCount += 1
                    require(entryCount <= limits.maxEntries) {
                        "Archive has more than ${limits.maxEntries} entries"
                    }
                    require(entry.size < 0 || entry.size <= limits.maxEntryBytes) {
                        "Archive entry ${entry.name} exceeds ${limits.maxEntryBytes} bytes"
                    }

                    val bytes = readEntry(zip, entry.name, limits.maxEntryBytes)
                    totalBytes += bytes.size
                    require(totalBytes <= limits.maxTotalBytes) {
                        "Archive exceeds ${limits.maxTotalBytes} uncompressed bytes"
                    }
                    result[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun readEntry(input: InputStream, entryName: String, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Archive entry $entryName exceeds $maxBytes bytes" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun requireSafeEntryName(name: String) {
        require(name.isNotBlank() && !name.startsWith('/') && !name.startsWith('\\')) {
            "Unsafe archive entry name: $name"
        }
        require(name.split('/').none { it == ".." }) { "Unsafe archive entry name: $name" }
    }
}

data class ZipLimits(
    val maxEntries: Int = 4_096,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxTotalBytes: Long = 128L * 1024 * 1024
)
