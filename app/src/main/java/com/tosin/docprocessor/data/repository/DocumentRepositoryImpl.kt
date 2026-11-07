package com.tosin.docprocessor.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.MimeTypes
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.data.parser.ParserFactory
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.writer.WriterFactory
import com.tosin.docprocessor.data.writer.DocumentOutputFormatResolver
import com.tosin.docprocessor.data.writer.common.LegacyDocumentExportAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import com.tosin.docprocessor.domain.document.model.ParsedDocument
import com.tosin.docprocessor.data.writer.common.DocumentTemplateFactory
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parserFactory: ParserFactory,
    private val writerFactory: WriterFactory,
    private val exportAdapter: LegacyDocumentExportAdapter
) : DocumentRepository {

    override suspend fun readDocumentFromUri(uri: Uri): ParseResult {
        val contentResolver = context.contentResolver
        val fileName = getFileName(uri)
        val mimeType = contentResolver.getType(uri) ?: MimeTypes.fromExtension(fileName.substringAfterLast('.', "")) ?: ""

        return contentResolver.openInputStream(uri)?.use { inputStream ->
            parseStreamReport(inputStream.readBytes(), mimeType)
        } ?: ParseResult(content = plainTextDocument("Failed to open file"))
    }

    override suspend fun saveDocumentToUri(uri: Uri, document: ParsedDocument) {
        val fileName = getFileName(uri)
        val mimeType = DocumentOutputFormatResolver.resolve(fileName, context.contentResolver.getType(uri))
        context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
            writeDocument(document, mimeType, outputStream)
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    override suspend fun getFileName(uri: Uri): String {
        var name = ""
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: "Untitled"
        }
        return name
    }


    override fun loadDocument(filePath: String): Flow<DocumentData> = flow {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        val document = parseDocument(filePath)
        emit(document)
    }

    override fun saveDocument(document: DocumentData): Flow<Unit> = flow {
        val file = File(document.id.ifEmpty { document.filename })
        file.outputStream().use { outputStream ->
            val name = file.name.ifBlank { document.filename }
            val mimeType = DocumentOutputFormatResolver.resolve(name, MimeTypes.fromExtension(document.format))
            val parsed = exportAdapter.adapt(document.content)
            writeDocument(parsed, mimeType, outputStream, document.content)
        }
        emit(Unit)
    }

    override suspend fun parseDocument(filePath: String): DocumentData =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            val fileName = file.name
            val mimeType = MimeTypes.fromExtension(file.extension).orEmpty()
            val content = file.inputStream().use { inputStream -> parseStream(inputStream, mimeType) }

            DocumentData(
                id = file.absolutePath,
                filename = fileName,
                content = content,
                format = file.extension
            )
        }

    override suspend fun createNewDocument(uri: Uri) {
        val fileName = getFileName(uri)
        val mimeType = DocumentOutputFormatResolver.resolve(fileName, context.contentResolver.getType(uri))
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            writeDocument(DocumentTemplateFactory.createDefault(mimeType), mimeType, outputStream, emptyList())
        } ?: throw IllegalStateException("Could not open output stream for URI: $uri")
    }

    private suspend fun parseStream(
        inputStream: java.io.InputStream,
        mimeType: String
    ): List<DocumentElement> {
        val parser = parserFactory.createLegacyParser(mimeType)
        return parser?.parse(inputStream)?.getOrThrow()
            ?: plainTextDocument(inputStream.bufferedReader().use { it.readText() })
    }

    /** Rich parse path: returns the full [ParseResult] with diagnostics and the legacy projection. */
    private suspend fun parseStreamReport(bytes: ByteArray, mimeType: String): ParseResult {
        val parser = parserFactory.createRichParser(bytes, mimeType)
            ?: return ParseResult(content = plainTextDocument(bytes.toString(Charsets.UTF_8)))
        return materializeResources(parser.parseWithReport(java.io.ByteArrayInputStream(bytes)).getOrThrow())
    }

    /**
     * Writes parsed resource bytes to the app cache so image renderers can load
     * them by file path, then remaps [DocumentElement.Image] source URIs to those
     * files. Logical URIs are preserved in the rich [ParseResult.document]; only
     * the temporary legacy projection is rewritten.
     */
    private fun materializeResources(result: ParseResult): ParseResult {
        if (result.document.resources.isEmpty()) return result
        val cacheDir = File(context.cacheDir, "media").apply { mkdirs() }
        val pathById = result.document.resources.mapValues { (id, resource) ->
            resource.bytes?.let { bytes ->
                val file = File(cacheDir, id.map { if (it == '/') '_' else it }.joinToString(""))
                if (!file.exists()) file.writeBytes(bytes)
                file.absolutePath
            }
        }
        if (pathById.values.none { it != null }) return result
        val remapped = result.content.map { element ->
            if (element is DocumentElement.Image) {
                pathById[element.sourceUri]?.let { element.copy(sourceUri = it) } ?: element
            } else {
                element
            }
        }
        return result.copy(content = remapped)
    }

    private fun plainTextDocument(text: String): List<DocumentElement> =
        listOf(DocumentElement.Paragraph(spans = listOf(TextSpan(text = text, color = "000000"))))

    private suspend fun writeDocument(
        document: ParsedDocument,
        mimeType: String,
        outputStream: java.io.OutputStream,
        plainTextContent: List<DocumentElement>? = null
    ) {
        val writer = writerFactory.createWriter(mimeType)
        if (writer != null) {
            writer.write(document, outputStream)
        } else if (mimeType.startsWith("text/")) {
            outputStream.writer(Charsets.UTF_8).apply {
                write(plainTextContent?.toPlainText() ?: document.blocks.joinToString("\n") { block ->
                    when (block) {
                        is com.tosin.docprocessor.domain.document.model.BlockNode.Paragraph ->
                            block.inlines.joinToString("") { inlineText(it) }
                        is com.tosin.docprocessor.domain.document.model.BlockNode.Heading ->
                            block.inlines.joinToString("") { inlineText(it) }
                        else -> ""
                    }
                })
                flush()
            }
        } else {
            throw IllegalArgumentException("Unsupported output format: $mimeType")
        }
    }

    private fun inlineText(inline: com.tosin.docprocessor.domain.document.model.InlineNode): String =
        when (inline) {
            is com.tosin.docprocessor.domain.document.model.InlineNode.Text -> inline.text
            is com.tosin.docprocessor.domain.document.model.InlineNode.Span -> inline.children.joinToString("") { inlineText(it) }
            is com.tosin.docprocessor.domain.document.model.InlineNode.Hyperlink -> inline.children.joinToString("") { inlineText(it) }
            is com.tosin.docprocessor.domain.document.model.InlineNode.Break -> "\n"
            is com.tosin.docprocessor.domain.document.model.InlineNode.Tab -> "\t"
            else -> ""
        }

    private fun List<DocumentElement>.toPlainText(): String =
        joinToString("\n") { element ->
            when (element) {
                is DocumentElement.Paragraph -> buildString {
                    element.listLabel?.let {
                        append(it)
                        append(' ')
                    }
                    append(element.spans.joinToString("") { it.text })
                }
                is DocumentElement.SectionHeader -> element.text
                is DocumentElement.Section -> element.properties.toString()
                is DocumentElement.HeaderFooter -> element.content.text
                is DocumentElement.Note -> element.info.text
                is DocumentElement.Comment -> element.info.text
                is DocumentElement.Bookmark -> element.info.name
                is DocumentElement.Field -> element.info.instruction
                is DocumentElement.Metadata -> "${element.info.title ?: element.info.kind}: ${element.info.summary}"
                is DocumentElement.Drawing -> element.info.kind
                is DocumentElement.EmbeddedObject -> element.info.description ?: element.info.kind
                is DocumentElement.Table -> element.rows.joinToString("\n") { row -> row.joinToString("\t") }
                is DocumentElement.Image -> element.caption ?: element.altText.orEmpty()
                is DocumentElement.PageBreak -> ""
            }
        }
}
