package com.tosin.docprocessor.data.parser.util

import java.security.MessageDigest

data class ArchiveParseDiagnostics(
    val entryCount: Int,
    val bytesProcessed: Long,
    val documentHash: String
)

fun Map<String, ByteArray>.toArchiveDiagnostics(primaryEntryPath: String): ArchiveParseDiagnostics {
    val primaryBytes = this[primaryEntryPath]
    return ArchiveParseDiagnostics(
        entryCount = size,
        bytesProcessed = values.sumOf { it.size.toLong() },
        documentHash = primaryBytes?.sha256() ?: "missing:$primaryEntryPath"
    )
}

fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append("%02x".format(byte))
        }
    }
}
