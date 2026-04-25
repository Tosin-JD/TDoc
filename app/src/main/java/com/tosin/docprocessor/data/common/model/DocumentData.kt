package com.tosin.docprocessor.data.common.model

data class DocumentData(
    val id: String = "",
    val filename: String = "",
    val content: List<DocumentElement> = emptyList(),
    val format: String = "", // "docx", "odt", etc.
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
