package com.tosin.docprocessor.data.local.entities

data class RecentFile(
    val uri: String,
    val fileName: String,
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val lastAccessed: Long = System.currentTimeMillis()
)
