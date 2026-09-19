package com.tosin.docprocessor.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.ParserFactory
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentRepositoryImplTest {

    private lateinit var repository: DocumentRepositoryImpl
    private lateinit var contentResolverShadow: ShadowContentResolver

    private val authority = "com.tosin.docprocessor.test"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = DocumentRepositoryImpl(context, ParserFactory(context))
        contentResolverShadow = shadowOf(context.contentResolver)
    }

    private fun uri(path: String): Uri = Uri.parse("content://$authority/$path")

    private fun registerProvider(fileName: String, mimeType: String?, size: Long = 0L) {
        ShadowContentResolver.registerProviderInternal(
            authority,
            StubProvider(fileName, mimeType, size)
        )
    }

    @Test
    fun `getDocumentMeta reads name size and mime from provider`() = runTest {
        registerProvider("notes.txt", "text/plain", size = 512L)

        val meta = repository.getDocumentMeta(uri("doc/notes.txt"))

        assertEquals("notes.txt", meta.fileName)
        assertEquals("text/plain", meta.mimeType)
        assertEquals(512L, meta.sizeBytes)
        assertEquals("content://$authority/doc/notes.txt", meta.uri)
    }

    @Test
    fun `getDocumentMeta falls back to txt when mime unknown`() = runTest {
        registerProvider("blob.bin", null)

        val meta = repository.getDocumentMeta(uri("doc/blob.bin"))

        assertEquals(MimeTypes.TXT, meta.mimeType)
        assertEquals("blob.bin", meta.fileName)
    }

    @Test
    fun `readDocumentFromUri parses supported text mime`() = runTest {
        registerProvider("notes.txt", "text/plain")
        val docUri = uri("doc/notes.txt")
        contentResolverShadow.registerInputStream(
            docUri,
            ByteArrayInputStream("Hello\nWorld".toByteArray())
        )

        val data = repository.readDocumentFromUri(docUri)

        val paragraphs = data.content.filterIsInstance<DocumentElement.Paragraph>()
        assertEquals(
            listOf("Hello", "World"),
            paragraphs.map { it.spans.joinToString("") { span -> span.text } }
        )
        assertEquals("notes.txt", data.filename)
    }

    @Test
    fun `readDocumentFromUri falls back to raw paragraph for unknown formats`() = runTest {
        registerProvider("blob.bin", "application/octet-stream")
        val docUri = uri("doc/blob.bin")
        contentResolverShadow.registerInputStream(
            docUri,
            ByteArrayInputStream("just some bytes".toByteArray())
        )

        val data = repository.readDocumentFromUri(docUri)

        val paragraphs = data.content.filterIsInstance<DocumentElement.Paragraph>()
        assertEquals(1, paragraphs.size)
        assertEquals("just some bytes", paragraphs.single().spans.single().text)
    }

    @Test
    fun `saveDocumentToUri writes flattened text through output stream`() = runTest {
        registerProvider("notes.txt", "text/plain")
        val docUri = uri("doc/notes.txt")
        val output = ByteArrayOutputStream()
        contentResolverShadow.registerOutputStream(docUri, output)

        repository.saveDocumentToUri(
            docUri,
            listOf(
                DocumentElement.Paragraph(spans = listOf(TextSpan(text = "one"))),
                DocumentElement.Table(rows = listOf(listOf("a", "b")))
            )
        )

        assertEquals("one\na\tb", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `createNewDocument rejects unsupported formats`() = runTest {
        registerProvider("thing.gif", "image/gif")
        val docUri = uri("doc/thing.gif")
        val output = ByteArrayOutputStream()
        contentResolverShadow.registerOutputStream(docUri, output)

        val result = runCatching { repository.createNewDocument(docUri, "thing.gif") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `createNewDocument writes a blank txt document`() = runTest {
        registerProvider("blank.txt", "text/plain")
        val docUri = uri("doc/blank.txt")
        val output = ByteArrayOutputStream()
        contentResolverShadow.registerOutputStream(docUri, output)

        runCatching { repository.createNewDocument(docUri, "blank.txt") }
            .onFailure { throw it }

        assertEquals("", output.toString(Charsets.UTF_8.name()))
    }

    private class StubProvider(
        private val fileName: String,
        private val mimeType: String?,
        private val size: Long
    ) : ContentProvider() {

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): Cursor = MatrixCursor(
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        ).apply {
            addRow(arrayOf<Any>(fileName, size))
        }

        override fun getType(uri: Uri): String? = mimeType

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<String>?
        ): Int = 0
    }
}