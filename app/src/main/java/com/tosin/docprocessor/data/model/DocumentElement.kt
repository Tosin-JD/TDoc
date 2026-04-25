package com.tosin.docprocessor.data.model

import android.graphics.Bitmap
import androidx.compose.ui.text.AnnotatedString

sealed class DocumentElement {
    data class Paragraph(val content: AnnotatedString) : DocumentElement()
    data class Table(val rows: List<List<String>>) : DocumentElement()
    data class Image(val bitmap: Bitmap, val altText: String) : DocumentElement()
    data class Header(val text: String) : DocumentElement()
    data class Footer(val text: String) : DocumentElement()
}
