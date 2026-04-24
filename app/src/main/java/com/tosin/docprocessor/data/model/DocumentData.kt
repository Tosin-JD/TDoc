package com.tosin.docprocessor.data.model

data class DocumentData(
    val id: String = "",
    val filename: String = "",
    val content: String = "",
    val format: String = "", // "docx", "odt", etc.
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
