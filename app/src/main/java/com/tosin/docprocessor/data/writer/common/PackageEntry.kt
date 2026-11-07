package com.tosin.docprocessor.data.writer.common

data class PackageEntry(
    val name: String,
    val bytes: ByteArray,
    val contentType: String? = null,
    val stored: Boolean = false
)
