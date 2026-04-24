package com.tosin.docprocessor.data.model

import androidx.compose.ui.text.AnnotatedString

data class DocumentData(
    val id: String = "",
    val filename: String = "",
    val content: AnnotatedString = AnnotatedString(""),
    val format: String = "", // "docx", "odt", etc.
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
