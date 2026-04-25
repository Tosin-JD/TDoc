package com.tosin.docprocessor.data.model

import androidx.compose.ui.text.AnnotatedString
import com.tosin.docprocessor.data.model.DocumentElement

data class DocumentData(
    val id: String = "",
    val filename: String = "",
    val content: List<DocumentElement> = emptyList(),
    val format: String = "", // "docx", "odt", etc.
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
)
