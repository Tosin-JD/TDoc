package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class DocxParserIntegrationTest {

    private fun parser(): DocxParser {
        val cacheDir = Files.createTempDirectory("docx-parser-test").toFile()
        return DocxParser(imageParser = DocxImageParser(cacheDir))
    }

    private fun parse(bytes: ByteArray): List<DocumentElement> =
        runBlocking {
            parser().parse(ByteArrayInputStream(bytes)).getOrThrow()
        }

    @Test
    fun testParseSimpleDocument() {
        val elements = parse(DocxTestFixtures.simpleDocx())
        val paragraphs = elements.filterIsInstance<DocumentElement.Paragraph>()
        val hello = paragraphs.first { it.spans.joinToString("") { s -> s.text } == "Hello world" }
        assertEquals(listOf("Hello world"), hello.spans.map { it.text })
        val styled = paragraphs.first { it.spans.any { s -> s.text == "Styled" } }
        assertTrue(styled.spans.first { it.text == "Styled" }.isBold)
        assertTrue(styled.spans.first { it.text == "Styled" }.isItalic)
        assertTrue(elements.any { it is DocumentElement.Section })
    }

    @Test
    fun testParseCorruptedDocument() {
        val result = runBlocking {
            parser().parse(ByteArrayInputStream(DocxTestFixtures.corruptedDocx()))
        }
        assertTrue("corrupted bytes should fail to parse", result.isFailure)
    }

    @Test
    fun testRoundTrip() {
        val first = parse(DocxTestFixtures.simpleDocx())
        val firstTexts = first
            .filterIsInstance<DocumentElement.Paragraph>()
            .map { it.spans.joinToString("") { s -> s.text } }
            .filter { it.isNotBlank() }
        assertTrue(firstTexts.contains("Hello world"))
        assertTrue(firstTexts.contains("Styled"))

        val saved = ByteArrayOutputStream()
        val saveResult = runBlocking { parser().save(saved, first) }
        assertTrue(saveResult.isSuccess)

        val second = parse(saved.toByteArray())
        val secondTexts = second
            .filterIsInstance<DocumentElement.Paragraph>()
            .map { it.spans.joinToString("") { s -> s.text } }
            .filter { it.isNotBlank() }
        // save() inserts metadata summaries (Section/Styles), so the original
        // body paragraphs must survive, not be an exact element-for-element match.
        assertTrue("round-trip lost body paragraphs: $secondTexts", secondTexts.containsAll(firstTexts))
    }
}