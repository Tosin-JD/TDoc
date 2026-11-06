package com.tosin.docprocessor.ui.editor.layouts.print

import android.graphics.RectF
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.ElementIndentation
import com.tosin.docprocessor.domain.model.layout.PositionedElement
import com.tosin.docprocessor.ui.editor.renderer.AnnotatedStringMappers
import com.tosin.docprocessor.ui.editor.renderer.DocumentTextStyleMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintLayoutWysiwygTest {

    private val textStyleMapper = DocumentTextStyleMapper()

    @Test
    fun `PositionedElement defaults have backwards compatibility and fragment fields`() {
        val paragraph = DocumentElement.Paragraph(spans = listOf(TextSpan("Sample text")))
        val positioned = PositionedElement(
            elementId = paragraph.id,
            element = paragraph,
            pageIndex = 0,
            bounds = RectF(10f, 20f, 100f, 50f)
        )

        assertEquals(paragraph.id, positioned.logicalElementId)
        assertEquals(0, positioned.fragmentIndex)
        assertFalse(positioned.isFragment)
        assertEquals(0, positioned.charStart)
        assertEquals(0, positioned.charEnd)
    }

    @Test
    fun `PositionedElement stores split fragment metadata correctly`() {
        val paragraph = DocumentElement.Paragraph(spans = listOf(TextSpan("Continuation text")))
        val fragmentElement = PositionedElement(
            elementId = "frag-2",
            element = paragraph,
            pageIndex = 1,
            bounds = RectF(10f, 20f, 100f, 50f),
            logicalElementId = "master-para-1",
            fragmentIndex = 1,
            isFragment = true,
            charStart = 40,
            charEnd = 80
        )

        assertEquals("master-para-1", fragmentElement.logicalElementId)
        assertEquals(1, fragmentElement.fragmentIndex)
        assertTrue(fragmentElement.isFragment)
        assertEquals(40, fragmentElement.charStart)
        assertEquals(80, fragmentElement.charEnd)
    }

    @Test
    fun `AnnotatedStringMappers splitSpans partitions text runs cleanly at boundary`() {
        val spans = listOf(
            TextSpan("Hello, ", isBold = true),
            TextSpan("wonderful world of WYSIWYG!")
        )
        // "Hello, " is 7 chars. Split at index 16 ("Hello, wonderful")
        val (spans1, spans2) = AnnotatedStringMappers.splitSpans(spans, 16)

        val text1 = spans1.joinToString("") { it.text }
        val text2 = spans2.joinToString("") { it.text }

        assertEquals("Hello, wonderful", text1)
        assertEquals(" world of WYSIWYG!", text2)
        assertTrue(spans1.first().isBold)
    }

    @Test
    fun `Fragment merging retains non-edited fragment text across page splits`() {
        // Simulating the master paragraph spanning page 1 and page 2:
        val masterSpans = listOf(
            TextSpan("Page 1 content here. "),
            TextSpan("Page 2 continuation here.")
        )
        val fullText = masterSpans.joinToString("") { it.text }
        val splitPoint = "Page 1 content here. ".length // 22 chars

        // Split spans as PrintPaginator does:
        val (fragment0Spans, fragment1Spans) = AnnotatedStringMappers.splitSpans(masterSpans, splitPoint)

        // User edits Fragment 0: "Page 1 edited content! "
        val updatedFrag0 = AnnotatedString("Page 1 edited content! ")
        val newFrag0Spans = AnnotatedStringMappers.toTextSpans(
            annotatedString = updatedFrag0,
            originalSpans = fragment0Spans
        )

        // Merge: new fragment 0 + unchanged fragment 1
        val mergedSpans = newFrag0Spans + fragment1Spans
        val mergedText = mergedSpans.joinToString("") { it.text }

        // Verify Fragment 1 on Page 2 was NOT deleted!
        assertTrue(mergedText.contains("Page 1 edited content! "))
        assertTrue(mergedText.contains("Page 2 continuation here."))
    }

    @Test
    fun `DocumentTextStyleMapper scales font size and line height with pageScale`() {
        val paragraph = DocumentElement.Paragraph(
            spans = listOf(TextSpan("Scaled text", fontSize = 16f))
        )
        val baseStyle = TextStyle(fontSize = 16.sp)
        val pageScale = 0.5f

        val scaledModel = textStyleMapper.map(
            paragraph = paragraph,
            baseStyle = baseStyle,
            isDarkMode = false,
            scale = pageScale,
            includeListLabel = true
        )

        // Base font size and span font size scaled by 0.5: 16 * 0.5 = 8sp
        assertEquals(8f, scaledModel.textStyle.fontSize.value, 0.01f)
        val spanSize = scaledModel.text.spanStyles.first().item.fontSize.value
        assertEquals(8f, spanSize, 0.01f)
    }

    @Test
    fun `DocumentTextStyleMapper excludes listLabel when includeListLabel is false`() {
        val paragraph = DocumentElement.Paragraph(
            spans = listOf(TextSpan("List body text")),
            listLabel = "•"
        )
        val baseStyle = TextStyle(fontSize = 14.sp)

        val modelWithoutLabel = textStyleMapper.map(
            paragraph = paragraph,
            baseStyle = baseStyle,
            isDarkMode = false,
            scale = 1f,
            includeListLabel = false
        )

        assertEquals("List body text", modelWithoutLabel.text.text)
        assertFalse(modelWithoutLabel.text.text.startsWith("•"))
    }

    @Test
    fun `DocumentTextStyleMapper scales indents proportionally with pageScale`() {
        val paragraph = DocumentElement.Paragraph(
            spans = listOf(TextSpan("Indented text")),
            style = ParagraphStyle(
                indentation = ParagraphIndentation(left = 720) // 720 / 20 = 36 dp
            )
        )
        val baseStyle = TextStyle(fontSize = 14.sp)

        val modelHalfScale = textStyleMapper.map(
            paragraph = paragraph,
            baseStyle = baseStyle,
            isDarkMode = false,
            scale = 0.5f
        )

        assertEquals(18.dp, modelHalfScale.indentStart)
    }
}
