package com.tosin.docprocessor.ui.editor.renderer

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextStyleMapperTest {

    private val mapper = DocumentTextStyleMapper()
    private val baseStyle = TextStyle(fontSize = 16.sp)

    private fun paragraph(
        alignment: ParagraphAlignment = ParagraphAlignment.START,
        spans: List<TextSpan> = listOf(TextSpan("hello")),
        listLabel: String? = null,
        indentation: ParagraphIndentation = ParagraphIndentation(),
        spacing: ParagraphSpacing = ParagraphSpacing()
    ) = DocumentElement.Paragraph(
        spans = spans,
        listLabel = listLabel,
        style = ParagraphStyle(alignment = alignment, indentation = indentation, spacing = spacing)
    )

    @Test
    fun `justified alignment maps to TextAlign Justify`() {
        val model = mapper.map(paragraph(ParagraphAlignment.JUSTIFIED), baseStyle, isDarkMode = false)
        assertEquals(TextAlign.Justify, model.textAlign)
    }

    @Test
    fun `center alignment maps to TextAlign Center`() {
        val model = mapper.map(paragraph(ParagraphAlignment.CENTER), baseStyle, isDarkMode = false)
        assertEquals(TextAlign.Center, model.textAlign)
    }

    @Test
    fun `indentation and spacing convert from twips to dp`() {
        val model = mapper.map(
            paragraph(
                indentation = ParagraphIndentation(left = 720, right = 360),
                spacing = ParagraphSpacing(before = 240, after = 120)
            ),
            baseStyle,
            isDarkMode = false
        )
        assertEquals(36.dp, model.indentStart)
        assertEquals(18.dp, model.indentEnd)
        assertEquals(12.dp, model.spaceBefore)
        assertEquals(6.dp, model.spaceAfter)
    }

    @Test
    fun `line height is raised so the tallest run is never clipped`() {
        val model = mapper.map(
            paragraph(spans = listOf(TextSpan("big", fontSize = 32))),
            baseStyle,
            isDarkMode = false
        )
        assertTrue(model.textStyle.lineHeight.value >= 32f * 1.25f)
    }

    @Test
    fun `list label and runs are concatenated in order`() {
        val model = mapper.map(
            paragraph(
                spans = listOf(TextSpan("Bold", isBold = true), TextSpan(" lead-in")),
                listLabel = "1."
            ),
            baseStyle,
            isDarkMode = false
        )
        assertEquals("1. Bold lead-in", model.text.text)
        assertTrue(model.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
}
