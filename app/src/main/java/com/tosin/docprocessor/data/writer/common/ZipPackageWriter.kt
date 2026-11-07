package com.tosin.docprocessor.data.writer.common

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ZipPackageWriter {
    fun write(entries: List<PackageEntry>, outputStream: OutputStream) {
        require(entries.map { it.name }.toSet().size == entries.size) { "Package contains duplicate entry names" }
        entries.forEach { entry ->
            require(entry.name.isNotBlank() && !entry.name.startsWith('/') &&
                entry.name.split('/').none { it == ".." || it.isEmpty() }
            ) { "Unsafe package entry name: ${entry.name}" }
        }
        ZipOutputStream(NonClosingOutputStream(outputStream)).use { zip ->
            entries.forEach { packageEntry ->
                val entry = ZipEntry(packageEntry.name)
                if (packageEntry.stored) {
                    val crc = CRC32().apply { update(packageEntry.bytes) }
                    entry.method = ZipEntry.STORED
                    entry.size = packageEntry.bytes.size.toLong()
                    entry.compressedSize = packageEntry.bytes.size.toLong()
                    entry.crc = crc.value
                }
                zip.putNextEntry(entry)
                zip.write(packageEntry.bytes)
                zip.closeEntry()
            }
        }
    }

    private class NonClosingOutputStream(outputStream: OutputStream) : FilterOutputStream(outputStream) {
        override fun close() = flush()
    }
}
