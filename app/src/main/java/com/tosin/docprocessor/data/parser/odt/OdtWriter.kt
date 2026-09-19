package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-written ODT serializer. Produces a deterministic ZIP with a stored
 * `mimetype` first entry, a `META-INF/manifest.xml`, `content.xml`, and any
 * images under `Pictures/`. Styles are generated inline as automatic styles,
 * deduplicated by property fingerprint, so the output round-trips through
 * [OdtXmlParser] with preserved formatting.
 */
class OdtWriter {

    private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private val styleNs = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val drawNs = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private val svgNs = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    private val foNs = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    private val xlinkNs = "http://www.w3.org/1999/xlink"
    private val manifestNs = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"

    private val paragraphStyles = LinkedHashMap<String, ParagraphDef>()
    private val characterStyles = LinkedHashMap<String, CharacterDef>()
    private val imageEntries = LinkedHashMap<String, String>() // href (Pictures/..) -> mime type

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    fun write(outputStream: OutputStream, content: List<DocumentElement>) {
        paragraphStyles.clear()
        characterStyles.clear()
        imageEntries.clear()

        val bodyXml = buildString { appendBody(content) }
        val stylesXml = buildString { appendAutomaticStyles() }
        val contentXml = buildContent(bodyXml, stylesXml)
        val manifestXml = buildManifest()

        ZipOutputStream(outputStream).use { zip ->
            writeStored(zip, "mimetype", "application/vnd.oasis.opendocument.text".toByteArray())
            write(zip, "META-INF/manifest.xml", manifestXml.toByteArray())
            write(zip, "content.xml", contentXml.toByteArray())
            imageEntries.keys.forEach { href ->
                val file = fileFromHref(href)
                if (file != null && file.isFile) {
                    write(zip, href, file.readBytes())
                }
            }
        }
    }

    fun writeBlank(outputStream: OutputStream) {
        write(outputStream, emptyList())
    }

    // ------------------------------------------------------------------
    // Document assembly
    // ------------------------------------------------------------------

    private fun buildContent(bodyXml: String, stylesXml: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<office:document-content")
        attr("xmlns:office", officeNs)
        attr("xmlns:style", styleNs)
        attr("xmlns:text", textNs)
        attr("xmlns:table", tableNs)
        attr("xmlns:draw", drawNs)
        attr("xmlns:svg", svgNs)
        attr("xmlns:fo", foNs)
        attr("xmlns:xlink", xlinkNs)
        attr("office:version", "1.2")
        append(">\n")
        append(stylesXml)
        append(bodyXml)
        append("</office:document-content>\n")
    }

    private fun StringBuilder.appendBody(content: List<DocumentElement>) {
        val headers = mutableListOf<DocumentElement.HeaderFooter>()
        val footers = mutableListOf<DocumentElement.HeaderFooter>()
        val main = mutableListOf<DocumentElement>()
        content.forEach { element ->
            when (element) {
                is DocumentElement.HeaderFooter ->
                    if (element.content.kind.name == "HEADER") headers += element
                    else footers += element
                else -> main += element
            }
        }
        append("<office:body>\n<office:text>\n")
        headers.forEach { appendHeaderFooter(it) }
        main.forEach { element ->
            when (element) {
                is DocumentElement.Paragraph -> appendParagraph(element)
                is DocumentElement.SectionHeader -> appendHeading(element)
                is DocumentElement.Table -> appendTable(element)
                is DocumentElement.Image -> appendImage(element)
                is DocumentElement.PageBreak -> appendPageBreak()
                is DocumentElement.Note -> appendNote(element)
                is DocumentElement.Bookmark -> appendBookmark(element)
                is DocumentElement.Field -> appendField(element)
                is DocumentElement.HeaderFooter -> appendHeaderFooter(element)
                is DocumentElement.Comment -> appendFallbackText(element.info.text)
                is DocumentElement.Drawing -> appendFallbackText(element.info.altText ?: element.info.kind)
                is DocumentElement.EmbeddedObject -> appendFallbackText(element.info.description ?: element.info.kind)
                is DocumentElement.Section -> appendFallbackText(element.properties.source)
                is DocumentElement.Metadata -> { /* metadata has no body representation in MVP */ }
            }
        }
        footers.forEach { appendHeaderFooter(it) }
        append("</office:text>\n</office:body>\n")
    }

    private fun StringBuilder.appendHeaderFooter(element: DocumentElement.HeaderFooter) {
        val footer = element.content.kind.name == "FOOTER"
        val tag = if (footer) "text:footer" else "text:header"
        append("  ").append('<').append(tag).append('>')
        if (element.content.text.isBlank()) {
            append("<text:p/>")
        } else {
            append("<text:p>").append(escapeXml(element.content.text)).append("</text:p>")
        }
        append("</").append(tag).append(">\n")
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    private fun StringBuilder.appendParagraph(element: DocumentElement.Paragraph) {
        if (element.listInfo != null) {
            appendListItem(element)
            return
        }
        val styleId = paragraphStyleId(element.style)
        append("  <text:p text:style-name=\"").append(styleId).append('"')
        element.style.outlineLevel?.let { append(" text:outline-level=\"").append(it).append('"') }
        append('>')
        if (element.hyperlink?.address != null || element.hyperlink?.anchor != null) {
            appendHyperlink(element.hyperlink!!) {
                appendSpans(element.spans)
            }
        } else {
            appendSpans(element.spans)
        }
        append("</text:p>\n")
    }

    private fun StringBuilder.appendListItem(element: DocumentElement.Paragraph) {
        append("  <text:list>\n")
        append("    <text:list-item>\n")
        element.listInfo?.levelText?.takeIf { it.isNotBlank() }?.let {
            append("      <text:number>").append(escapeXml(it)).append("</text:number>\n")
        }
        val inner = element.copy(listInfo = null)
        val styleId = paragraphStyleId(inner.style)
        append("      <text:p text:style-name=\"").append(styleId).append('"')
        inner.style.outlineLevel?.let { append(" text:outline-level=\"").append(it).append('"') }
        append('>')
        appendSpans(inner.spans)
        append("</text:p>\n")
        append("    </text:list-item>\n")
        append("  </text:list>\n")
    }

    private fun StringBuilder.appendHeading(element: DocumentElement.SectionHeader) {
        val text = element.text
        append("  <text:h text:outline-level=\"").append(element.level).append('"')
        if (text.isNotBlank()) {
            val headingCharId = charStyleId(
                CharStyleKey(isBold = true, isItalic = false, isUnderline = false, color = null, fontSize = null, fontFamily = null)
            )
            append(" text:style-name=\"").append(headingCharId).append('"')
            append('>')
            if (headingCharId != null) {
                append("<text:span text:style-name=\"").append(headingCharId).append("\">")
                append(escapeXml(text))
                append("</text:span>")
            } else {
                append(escapeXml(text))
            }
        }
        append("</text:h>\n")
    }

    private fun StringBuilder.appendTable(element: DocumentElement.Table) {
        append("  <table:table>\n")
        element.rows.forEachIndexed { rowIndex, row ->
            append("    <table:table-row>\n")
            val metaRow = element.metadata.rows.getOrNull(rowIndex)
            row.forEachIndexed { colIndex, cellText ->
                val cellMeta = metaRow?.getOrNull(colIndex)
                append("      <table:table-cell")
                cellMeta?.gridSpan?.takeIf { it > 1 }?.let {
                    append(" table:number-columns-spanned=\"").append(it).append('"')
                }
                append(">\n")
                appendCellParagraphs(cellText)
                append("      </table:table-cell>\n")
            }
            append("    </table:table-row>\n")
        }
        append("  </table:table>\n")
    }

    private fun StringBuilder.appendCellParagraphs(cellText: String) {
        val lines = cellText.split("\n")
        if (lines.isEmpty() || cellText.isEmpty()) {
            append("        <text:p/>\n")
            return
        }
        lines.forEach { line ->
            append("        <text:p>")
            if (line.isNotBlank()) {
                append(escapeXml(line))
            }
            append("</text:p>\n")
        }
    }

    private fun StringBuilder.appendImage(element: DocumentElement.Image) {
        val safeName = fileNameFromUri(element.sourceUri)?.sanitizeFileName() ?: return
        val hrefPath = "Pictures/$safeName"
        val mediaType = mediaTypeOf(safeName)
        imageEntries[hrefPath] = mediaType

        append("  <text:p>\n")
        append("    <draw:frame draw:name=\"Image\" text:anchor-type=\"as-char\" svg:width=\"5cm\" svg:height=\"3cm\" draw:z-index=\"0\">\n")
        append("      <draw:image xlink:href=\"").append(hrefPath)
            .append("\" xlink:type=\"simple\" xlink:show=\"embed\" xlink:actuate=\"onLoad\"/>\n")
        element.altText?.takeIf { it.isNotBlank() }?.let {
            append("      <svg:desc>").append(escapeXml(it)).append("</svg:desc>\n")
        }
        append("    </draw:frame>\n")
        append("  </text:p>\n")
    }

    private fun StringBuilder.appendPageBreak() {
        val styleId = paragraphStyleId(ParagraphStyleKey(pageBreakBefore = true))
        append("  <text:p text:style-name=\"").append(styleId).append("\"/>\n")
    }

    private fun StringBuilder.appendNote(element: DocumentElement.Note) {
        val noteClass = if (element.info.kind.name == "ENDNOTE") "endnote" else "footnote"
        append("  <text:p>\n")
        append("    <text:note text:note-class=\"").append(noteClass).append("\">\n")
        append("      <text:note-citation/>\n")
        append("      <text:note-body>\n")
        append("        <text:p>").append(escapeXml(element.info.text)).append("</text:p>\n")
        append("      </text:note-body>\n")
        append("    </text:note>\n")
        append("  </text:p>\n")
    }

    private fun StringBuilder.appendBookmark(element: DocumentElement.Bookmark) {
        append("  <text:bookmark text:name=\"").append(escapeXml(element.info.name)).append("\"/>\n")
    }

    private fun StringBuilder.appendField(element: DocumentElement.Field) {
        val value = element.info.value.orEmpty()
        if (value.isBlank()) {
            append("  <text:p/>\n")
        } else {
            append("  <text:p>").append(escapeXml(value)).append("</text:p>\n")
        }
    }

    private fun StringBuilder.appendFallbackText(text: String) {
        if (text.isBlank()) {
            append("  <text:p/>\n")
        } else {
            append("  <text:p>").append(escapeXml(text)).append("</text:p>\n")
        }
    }

    // ------------------------------------------------------------------
    // Spans / inline
    // ------------------------------------------------------------------

    private fun StringBuilder.appendSpans(spans: List<TextSpan>) {
        spans.forEach { span ->
            val styleId = charStyleId(CharStyleKey.from(span))
            if (styleId != null) {
                append("<text:span text:style-name=\"").append(styleId).append("\">")
                append(escapeXml(span.text))
                append("</text:span>")
            } else {
                append(escapeXml(span.text))
            }
        }
    }

    private inline fun StringBuilder.appendHyperlink(
        hyperlink: HyperlinkInfo,
        content: StringBuilder.() -> Unit
    ) {
        val target = hyperlink.address ?: "#${hyperlink.anchor}"
        append("<text:a xlink:href=\"").append(escapeXml(target)).append("\" xlink:type=\"simple\" xlink:show=\"replace\" xlink:actuate=\"onRequest\">")
        content()
        append("</text:a>")
    }

    // ------------------------------------------------------------------
    // Styles
    // ------------------------------------------------------------------

    private fun paragraphStyleId(style: ParagraphStyle): String {
        val key = ParagraphStyleKey(
            alignment = style.alignment,
            indentLeft = style.indentation.left,
            indentRight = style.indentation.right,
            firstLine = style.indentation.firstLine,
            hanging = style.indentation.hanging,
            spacingBefore = style.spacing.before,
            spacingAfter = style.spacing.after,
            spacingLine = style.spacing.line
        )
        return paragraphStyleId(key)
    }

    private fun paragraphStyleId(key: ParagraphStyleKey): String {
        val fingerprint = key.toString()
        paragraphStyles[fingerprint]?.let { return it.id }
        val id = "P${paragraphStyles.size + 1}"
        paragraphStyles[fingerprint] = ParagraphDef(id, key)
        return id
    }

    private fun charStyleId(key: CharStyleKey): String? {
        if (!key.hasFormatting()) return null
        val fingerprint = key.toString()
        characterStyles[fingerprint]?.let { return it.id }
        val id = "T${characterStyles.size + 1}"
        characterStyles[fingerprint] = CharacterDef(id, key)
        return id
    }

    private fun StringBuilder.appendAutomaticStyles() {
        if (paragraphStyles.isEmpty() && characterStyles.isEmpty()) return
        append("<office:automatic-styles>\n")
        paragraphStyles.forEach { (fingerprint, def) ->
            append("  <style:style style:family=\"paragraph\" style:name=\"")
                .append(def.id).append("\">\n")
            appendParagraphProperties(def.key)
            append("  </style:style>\n")
        }
        characterStyles.forEach { (fingerprint, def) ->
            append("  <style:style style:family=\"text\" style:name=\"")
                .append(def.id).append("\">\n")
            appendCharacterProperties(def.key)
            append("  </style:style>\n")
        }
        append("</office:automatic-styles>\n")
    }

    private fun StringBuilder.appendParagraphProperties(key: ParagraphStyleKey) {
        append("    <style:paragraph-properties")
        key.alignment?.let { alignment ->
            val value = when (alignment) {
                ParagraphAlignment.START -> "start"
                ParagraphAlignment.END -> "end"
                ParagraphAlignment.CENTER -> "center"
                ParagraphAlignment.JUSTIFIED -> "justify"
                ParagraphAlignment.DISTRIBUTED -> "justify"
            }
            attr("fo:text-align", value)
        }
        twipsToIn(key.indentLeft)?.let { attr("fo:margin-left", it) }
        twipsToIn(key.indentRight)?.let { attr("fo:margin-right", it) }
        val textIndent = key.firstLine?.let { twipsToIn(it) }
            ?: key.hanging?.let { twipsToIn(it)?.let { v -> "-$v" } }
        textIndent?.let { attr("fo:text-indent", it) }
        twipsToPt(key.spacingBefore)?.let { attr("fo:space-before", it) }
        twipsToPt(key.spacingAfter)?.let { attr("fo:space-after", it) }
        twipsToPt(key.spacingLine)?.let { attr("fo:line-height", it) }
        if (key.pageBreakBefore) attr("fo:break-before", "page")
        append("/>\n")
    }

    private fun StringBuilder.appendCharacterProperties(key: CharStyleKey) {
        append("    <style:text-properties")
        if (key.isBold) attr("fo:font-weight", "bold")
        if (key.isItalic) attr("fo:font-style", "italic")
        if (key.isUnderline) attr("style:text-underline-style", "solid")
        key.color?.takeIf { it.isNotBlank() }?.let { attr("fo:color", "#$it") }
        key.fontSize?.let { attr("fo:font-size", "${it}pt") }
        key.fontFamily?.takeIf { it.isNotBlank() }?.let { attr("fo:font-family", it) }
        append("/>\n")
    }

    // ------------------------------------------------------------------
    // Manifest / zip
    // ------------------------------------------------------------------

    private fun buildManifest(): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<manifest:manifest xmlns:manifest=\"").append(manifestNs)
            .append("\" manifest:version=\"1.2\">\n")
        append("  <manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\" manifest:version=\"1.2\"/>\n")
        append("  <manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>\n")
        imageEntries.forEach { (href, mediaType) ->
            append("  <manifest:file-entry manifest:full-path=\"").append(href)
                .append("\" manifest:media-type=\"").append(mediaType).append("\"/>\n")
        }
        append("</manifest:manifest>\n")
    }

    private fun writeStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun write(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun StringBuilder.attr(name: String, value: String) {
        append(' ').append(name).append("=\"").append(escapeXml(value)).append('"')
    }

    private fun escapeXml(text: String): String = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private fun twipsToIn(twips: Int?): String? = twips?.let { "%.3fin".format(it / 1440.0) }

    private fun twipsToPt(twips: Int?): String? = twips?.let { "%.1fpt".format(it / 20.0) }

    private fun fileNameFromUri(uri: String): String? = try {
        File(URI(uri)).name
    } catch (e: Exception) {
        uri.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    private fun fileFromHref(href: String): File? = try {
        File(URI("file:$href"))
    } catch (e: Exception) {
        null
    }

    private fun mediaTypeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image.bin" }

    // ------------------------------------------------------------------
    // Style keys
    // ------------------------------------------------------------------

    data class ParagraphStyleKey(
        val alignment: ParagraphAlignment? = null,
        val indentLeft: Int? = null,
        val indentRight: Int? = null,
        val firstLine: Int? = null,
        val hanging: Int? = null,
        val spacingBefore: Int? = null,
        val spacingAfter: Int? = null,
        val spacingLine: Int? = null,
        val pageBreakBefore: Boolean = false
    )

    private data class ParagraphDef(val id: String, val key: ParagraphStyleKey)

    private data class CharacterDef(val id: String, val key: CharStyleKey)

    data class CharStyleKey(
        val isBold: Boolean,
        val isItalic: Boolean,
        val isUnderline: Boolean,
        val color: String?,
        val fontSize: Int?,
        val fontFamily: String?
    ) {
        fun hasFormatting(): Boolean =
            isBold || isItalic || isUnderline ||
                color != null || fontSize != null || fontFamily != null

        companion object {
            fun from(span: TextSpan): CharStyleKey = CharStyleKey(
                isBold = span.isBold,
                isItalic = span.isItalic,
                isUnderline = span.isUnderline,
                color = span.color,
                fontSize = span.fontSize,
                fontFamily = span.fontFamily
            )
        }
    }
}