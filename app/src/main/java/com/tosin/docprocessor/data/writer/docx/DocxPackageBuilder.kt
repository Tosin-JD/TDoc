package com.tosin.docprocessor.data.writer.docx

import com.tosin.docprocessor.data.writer.common.PackageEntry
import com.tosin.docprocessor.data.writer.common.XmlSerializerSupport
import com.tosin.docprocessor.data.writer.common.XmlTextSanitizer
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument

internal class DocxPackageBuilder(
    private val serializer: DocxXmlSerializer = DocxXmlSerializer()
) {
    fun build(document: ParsedDocument): List<PackageEntry> {
        val referencedIds = referencedResources(document.blocks)
        val media = linkedMapOf<String, Pair<String, ByteArray>>()
        val imageRelationshipIds = linkedMapOf<String, String>()
        referencedIds.forEach { id ->
            val resource = document.resources[id] ?: return@forEach
            val bytes = resource.bytes ?: return@forEach
            if (resource.isExternal) return@forEach
            val extension = extensionFor(resource.mimeType)
            val path = "word/media/image${media.size + 1}.$extension"
            val relationshipId = "rId${media.size + if (document.blocks.any(::containsList)) 3 else 2}"
            media[id] = path to bytes
            imageRelationshipIds[id] = relationshipId
        }
        val hasNumbering = document.blocks.any(::containsList)
        val entries = mutableListOf(
            PackageEntry("[Content_Types].xml", contentTypes(media.values.map { it.first }, hasNumbering).validated()),
            PackageEntry("_rels/.rels", rootRelationships().validated()),
            PackageEntry("word/document.xml", serializer.document(document, imageRelationshipIds).validated()),
            PackageEntry("word/styles.xml", serializer.styles(document).validated()),
            PackageEntry("docProps/core.xml", coreProperties(document).validated()),
            PackageEntry(
                "word/_rels/document.xml.rels",
                documentRelationships(media, hasNumbering).validated()
            )
        )
        if (hasNumbering) entries += PackageEntry("word/numbering.xml", serializer.numbering().validated())
        media.forEach { (_, pair) -> entries += PackageEntry(pair.first, pair.second) }
        return entries
    }

    private fun contentTypes(mediaPaths: List<String>, hasNumbering: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""")
        mediaPaths.map { it.substringAfterLast('.') }.distinct().forEach { extension ->
            val mime = mimeType(extension)
            append("""<Default Extension="$extension" ContentType="$mime"/>""")
        }
        append("""<Override PartName="/word/document.xml" ContentType="${DocxPackageConstants.MAIN_TYPE}"/><Override PartName="/word/styles.xml" ContentType="${DocxPackageConstants.STYLES_TYPE}"/>""")
        append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
        if (hasNumbering) append("""<Override PartName="/word/numbering.xml" ContentType="${DocxPackageConstants.NUMBERING_TYPE}"/>""")
        append("</Types>")
    }

    private fun rootRelationships() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="${DocxPackageConstants.DOCUMENT_REL}" Target="word/document.xml"/>
        <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
        </Relationships>""".trimIndent()

    private fun coreProperties(document: ParsedDocument): String {
        val metadata = document.metadata
        fun element(name: String, value: String?) =
            value?.let { "<dc:$name>${XmlTextSanitizer.escapeText(it)}</dc:$name>" }.orEmpty()
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
              xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:dcterms="http://purl.org/dc/terms/"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              ${element("title", metadata.title)}${element("subject", metadata.subject)}
              ${element("creator", metadata.creator)}${element("description", metadata.description)}
              ${metadata.keywords.takeIf { it.isNotEmpty() }?.let { "<cp:keywords>${XmlTextSanitizer.escapeText(it.joinToString(", "))}</cp:keywords>" }.orEmpty()}
              ${metadata.lastModifiedBy?.let { "<cp:lastModifiedBy>${XmlTextSanitizer.escapeText(it)}</cp:lastModifiedBy>" }.orEmpty()}
            </cp:coreProperties>""".trimIndent()
    }

    private fun documentRelationships(
        media: Map<String, Pair<String, ByteArray>>,
        hasNumbering: Boolean
    ) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="${DocxPackageConstants.STYLES_REL}" Target="styles.xml"/>""")
        if (hasNumbering) append("""<Relationship Id="rId2" Type="${DocxPackageConstants.NUMBERING_REL}" Target="numbering.xml"/>""")
        media.entries.forEachIndexed { index, entry ->
            val id = "rId${index + if (hasNumbering) 3 else 2}"
            append("""<Relationship Id="$id" Type="${DocxPackageConstants.IMAGE_REL}" Target="${entry.value.first.removePrefix("word/")}"/>""")
        }
        append("</Relationships>")
    }

    private fun referencedResources(blocks: List<BlockNode>): Set<String> = buildSet {
        lateinit var visitInline: (InlineNode) -> Unit
        lateinit var visitBlock: (BlockNode) -> Unit
        visitInline = { node ->
            when (node) {
                is InlineNode.Drawing -> node.resourceId?.let(::add)
                is InlineNode.Span -> node.children.forEach(visitInline)
                is InlineNode.Hyperlink -> node.children.forEach(visitInline)
                is InlineNode.Revision -> node.children.forEach(visitInline)
                is InlineNode.ContentControl -> node.children.forEach(visitInline)
                is InlineNode.Field -> node.result.forEach(visitInline)
                is InlineNode.Ruby -> (node.base + node.rubyText).forEach(visitInline)
                is InlineNode.TextBox -> node.blocks.forEach(visitBlock)
                else -> Unit
            }
        }
        visitBlock = { node ->
            when (node) {
                is BlockNode.Figure -> node.resourceId?.let(::add)
                is BlockNode.Paragraph -> node.inlines.forEach(visitInline)
                is BlockNode.Heading -> node.inlines.forEach(visitInline)
                is BlockNode.ListBlock -> node.items.forEach { it.blocks.forEach(visitBlock) }
                is BlockNode.ListItem -> node.blocks.forEach(visitBlock)
                is BlockNode.Table -> node.rows.forEach { row -> row.cells.forEach { it.blocks.forEach(visitBlock) } }
                is BlockNode.TextBox -> node.blocks.forEach(visitBlock)
                is BlockNode.NoteBody, is BlockNode.RevisionBlock -> when (node) {
                    is BlockNode.NoteBody -> node.blocks.forEach(visitBlock)
                    is BlockNode.RevisionBlock -> node.blocks.forEach(visitBlock)
                    else -> Unit
                }
                else -> Unit
            }
        }
        blocks.forEach(visitBlock)
    }

    private fun containsList(node: BlockNode): Boolean = when (node) {
        is BlockNode.ListBlock -> true
        is BlockNode.Paragraph -> node.listMarker != null
        is BlockNode.ListItem -> node.blocks.any(::containsList)
        is BlockNode.Table -> node.rows.any { row -> row.cells.any { it.blocks.any(::containsList) } }
        else -> false
    }

    private fun extensionFor(mime: String?): String = when (mime?.lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpeg"
        "image/gif" -> "gif"
        "image/svg+xml" -> "svg"
        "image/bmp" -> "bmp"
        "image/webp" -> "webp"
        else -> "bin"
    }

    private fun mimeType(extension: String): String = when (extension) {
        "png" -> "image/png"
        "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun String.validated(): ByteArray {
        XmlSerializerSupport.validate(this)
        return utf8()
    }

    private fun String.utf8() = XmlTextSanitizer.sanitize(this).toByteArray(Charsets.UTF_8)
}
