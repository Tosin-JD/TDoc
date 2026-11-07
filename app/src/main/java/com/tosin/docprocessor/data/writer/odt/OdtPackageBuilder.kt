package com.tosin.docprocessor.data.writer.odt

import com.tosin.docprocessor.data.writer.common.PackageEntry
import com.tosin.docprocessor.data.writer.common.XmlSerializerSupport
import com.tosin.docprocessor.data.writer.common.XmlTextSanitizer
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.ParsedDocument

internal class OdtPackageBuilder(
    private val serializer: OdtXmlSerializer = OdtXmlSerializer()
) {
    fun build(document: ParsedDocument): List<PackageEntry> {
        val imagePaths = linkedMapOf<String, String>()
        referencedResources(document.blocks).forEach { id ->
            val resource = document.resources[id] ?: return@forEach
            if (resource.isExternal || resource.bytes == null) return@forEach
            val ext = when (resource.mimeType?.lowercase()) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/gif" -> "gif"
                "image/svg+xml" -> "svg"
                "image/webp" -> "webp"
                else -> "bin"
            }
            imagePaths[id] = "Pictures/image${imagePaths.size + 1}.$ext"
        }
        val content = serializer.content(document, imagePaths)
        val styles = serializer.styles()
        val meta = serializer.metadata(document)
        val manifest = manifest(document, imagePaths)
        val entries = mutableListOf(
            PackageEntry("mimetype", OdtPackageConstants.MIME_TYPE.toByteArray(Charsets.UTF_8), stored = true),
            PackageEntry("content.xml", content.validated()),
            PackageEntry("styles.xml", styles.validated()),
            PackageEntry("meta.xml", meta.validated()),
            PackageEntry("META-INF/manifest.xml", manifest.validated())
        )
        imagePaths.forEach { (id, path) ->
            val bytes = document.resources[id]?.bytes ?: return@forEach
            entries += PackageEntry(path, bytes)
        }
        return entries
    }

    private fun manifest(document: ParsedDocument, imagePaths: Map<String, String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">""")
        append("""<manifest:file-entry manifest:full-path="/" manifest:media-type="${OdtPackageConstants.MIME_TYPE}"/>""")
        append("""<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>""")
        append("""<manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>""")
        append("""<manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>""")
        imagePaths.forEach { (id, path) ->
            val type = document.resources[id]?.mimeType ?: "application/octet-stream"
            append("""<manifest:file-entry manifest:full-path="$path" manifest:media-type="${XmlTextSanitizer.escapeAttribute(type)}"/>""")
        }
        append("</manifest:manifest>")
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
                is BlockNode.NoteBody -> node.blocks.forEach(visitBlock)
                is BlockNode.RevisionBlock -> node.blocks.forEach(visitBlock)
                else -> Unit
            }
        }
        blocks.forEach(visitBlock)
    }

    private fun String.validated(): ByteArray {
        XmlSerializerSupport.validate(this)
        return toByteArray(Charsets.UTF_8)
    }
}
