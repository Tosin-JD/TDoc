package com.tosin.docprocessor.data.parser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficePackageSafetyTest {

    @Test
    fun `extracts valid office package entries`() {
        val archive = zipOf("word/document.xml" to "<document/>".toByteArray())

        val entries = DefaultZipExtractor().extract(ByteArrayInputStream(archive))

        assertEquals("<document/>", entries.getValue("word/document.xml").decodeToString())
    }

    @Test
    fun `rejects path traversal entries`() {
        val archive = zipOf("../word/document.xml" to byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            DefaultZipExtractor().extract(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun `rejects oversized entries`() {
        val archive = zipOf("word/document.xml" to ByteArray(16))

        assertThrows(IllegalArgumentException::class.java) {
            DefaultZipExtractor(ZipLimits(maxEntryBytes = 8, maxTotalBytes = 32))
                .extract(ByteArrayInputStream(archive))
        }
    }

    @Test
    fun `does not permit external entity declarations`() {
        val xml = """<!DOCTYPE root [<!ENTITY injected SYSTEM "file:///not-allowed">]><root>&injected;</root>"""

        assertThrows(Exception::class.java) { SecureXml.parse(xml.toByteArray()) }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
