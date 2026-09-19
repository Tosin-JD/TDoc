package com.tosin.docprocessor.model

data class DocumentMeta(
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val lastOpened: Long = System.currentTimeMillis()
)