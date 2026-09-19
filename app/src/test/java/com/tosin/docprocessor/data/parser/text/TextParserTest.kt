package com.tosin.docprocessor.data.parser.text

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextParserTest {

    private val parser = TextParser()

    @Test
    fun `parse empty input returns empty list`() = runBlocking {
        val result = parser.parse(ByteArrayInputStream("".toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(emptyList<DocumentElement>(), result.getOrThrow())
    }

    @Test
    fun `parse single line returns one paragraph`() = runBlocking {
        val result = parser.parse(ByteArrayInputStream("hello".toByteArray()))
        val paragraphs = result.getOrThrow()
        assertEquals(1, paragraphs.size)
        val paragraph = paragraphs[0] as DocumentElement.Paragraph
        assertEquals(listOf(TextSpan(text = "hello")), paragraph.spans)
    }

    @Test
    fun `parse multiple lines returns paragraph per line`() = runBlocking {
        val result = parser.parse(ByteArrayInputStream("line1\nline2\nline3".toByteArray()))
        val paragraphs = result.getOrThrow().filterIsInstance<DocumentElement.Paragraph>()
        assertEquals(
            listOf("line1", "line2", "line3"),
            paragraphs.map { it.spans.joinToString("") { span -> span.text } }
        )
    }

    @Test
    fun `parse skips blank lines`() = runBlocking {
        val result = parser.parse(ByteArrayInputStream("a\n\n\nb\n".toByteArray()))
        val paragraphs = result.getOrThrow().filterIsInstance<DocumentElement.Paragraph>()
        assertEquals(
            listOf("a", "b"),
            paragraphs.map { it.spans.joinToString("") { span -> span.text } }
        )
    }

    @Test
    fun `save writes paragraphs joined by newline`() = runBlocking {
        val content = listOf(
            DocumentElement.Paragraph(spans = listOf(TextSpan(text = "one"))),
            DocumentElement.Paragraph(spans = listOf(TextSpan(text = "two")))
        )
        val output = ByteArrayOutputStream()
        val result = parser.save(output, content)
        assertTrue(result.isSuccess)
        assertEquals("one\ntwo", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `save flattens non-paragraph elements into text`() = runBlocking {
        val content = listOf(
            DocumentElement.Paragraph(spans = listOf(TextSpan(text = "text"))),
            DocumentElement.Table(rows = listOf(listOf("a", "b"))),
            DocumentElement.Image(sourceUri = "file:/tmp/x.png", altText = "alt")
        )
        val output = ByteArrayOutputStream()
        parser.save(output, content)
        assertEquals("text\na\tb\nalt", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `blank document round-trips to empty parse`() = runBlocking {
        val output = ByteArrayOutputStream()
        val created = parser.createBlankDocument(output, "text/plain")
        assertTrue(created.isSuccess)
        val parsed = parser.parse(ByteArrayInputStream(output.toByteArray()))
        assertEquals(emptyList<DocumentElement>(), parsed.getOrThrow())
    }

    @Test
    fun `full round trip preserves paragraphs`() = runBlocking {
        val original = "first line\nsecond line\nthird"
        val parsed = parser.parse(ByteArrayInputStream(original.toByteArray())).getOrThrow()
        val output = ByteArrayOutputStream()
        parser.save(output, parsed)
        val reparsed = parser.parse(ByteArrayInputStream(output.toByteArray())).getOrThrow()
        assertEquals(parsed, reparsed)
    }
}