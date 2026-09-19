package com.tosin.docprocessor.data.parser.odt

import android.content.Context
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.DocumentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class OdtParser(
    private val context: Context
) : DocumentParser {

    private val zipExtractor = OdtZipExtractor()
    private val metadataParser = OdtMetadataParser()
    private val writer = OdtWriter()

    override suspend fun parse(inputStream: InputStream): Result<List<DocumentElement>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entries = zipExtractor.extractAllEntries(inputStream)
                val contentXml = entries["content.xml"]
                    ?: throw IllegalArgumentException("Not a valid ODT file: missing content.xml")

                val elements = mutableListOf<DocumentElement>()

                // 1. Metadata
                val metaXml = entries["meta.xml"]
                metadataParser.parse(metaXml)?.let { elements += it }

                // 2. Content (content.xml automatic styles + styles.xml defaults merged)
                val xmlParser = OdtXmlParser(context.cacheDir, entries)
                elements += xmlParser.parse(contentXml, entries["styles.xml"])

                elements
            }
        }

    override suspend fun save(outputStream: OutputStream, content: List<DocumentElement>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                writer.write(outputStream, content)
            }
        }

    override suspend fun createBlankDocument(
        outputStream: OutputStream,
        mimeType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            writer.writeBlank(outputStream)
            Unit
        }
    }
}