package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.w3c.dom.Document
import org.w3c.dom.Element

class DocxAdvancedMarkupExtractor : DocxPackageExtractor {

    override fun extract(document: XWPFDocument, docxPackage: DocxPackage): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        val mainDocument = docxPackage.xml("word/document.xml")
        val headerFooterDocuments = docxPackage.paths("word/header") + docxPackage.paths("word/footer")

        mainDocument?.let { xml ->
            extractContentControls(xml)?.let(output::add)
            extractRevisions(xml)?.let(output::add)
            extractReferences(xml, docxPackage)?.let(output::add)
            extractMath(xml)?.let(output::add)
            extractAlternateContent(xml)?.let(output::add)
            extractPageLayout(xml)?.let(output::add)
            extractBordersAndShading(xml)?.let(output::add)
            extractBiDiAndComplexScript(xml)?.let(output::add)
        }

        headerFooterDocuments.forEach { path ->
            docxPackage.xml(path)?.let { xml ->
                extractWatermark(path, xml, docxPackage)?.let(output::add)
                extractAlternateContent(xml, source = path)?.let(output::add)
            }
        }

        extractCitations(docxPackage)?.let(output::add)
        return output
    }

    private fun extractContentControls(document: Document): DocumentElement.Metadata? {
        val controls = document.documentElement
            ?.descendants("sdt")
            .orEmpty()
            .mapIndexed { index, sdt ->
                val properties = sdt.firstChild("sdtPr")
                val alias = properties?.firstChild("alias")?.attribute("val")
                val tag = properties?.firstChild("tag")?.attribute("val")
                val type = resolveContentControlType(properties)
                val listItems = properties?.descendants("listItem").orEmpty()
                MetadataInfo(
                    kind = "content-control",
                    title = alias ?: tag ?: "Control ${index + 1}",
                    summary = listOfNotNull(
                        "type=$type",
                        tag?.let { "tag=$it" },
                        properties?.firstChild("id")?.attribute("val")?.let { "id=$it" },
                        listItems.takeIf { it.isNotEmpty() }?.let { "items=${it.size}" }
                    ).joinToString(", "),
                    attributes = buildMap {
                        put("type", type)
                        alias?.let { put("title", it) }
                        tag?.let { put("tag", it) }
                        properties?.firstChild("id")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                            put("id", it)
                        }
                    },
                    source = "word/document.xml"
                )
            }
        if (controls.isNullOrEmpty()) {
            return null
        }
        return metadata(
            kind = "content-controls",
            title = "Content Controls",
            summary = "${controls.size} content controls",
            children = controls,
            source = "word/document.xml"
        )
    }

    private fun extractRevisions(document: Document): DocumentElement.Metadata? {
        val revisionElements = listOf(
            "ins" to "insertion",
            "del" to "deletion",
            "moveFrom" to "move-from",
            "moveTo" to "move-to",
            "pPrChange" to "paragraph-change",
            "rPrChange" to "run-change"
        )

        val revisions = revisionElements.flatMap { (tag, kind) ->
            document.documentElement
                ?.descendants(tag)
                .orEmpty()
                .mapIndexed { index, node ->
                    MetadataInfo(
                        kind = "revision",
                        title = "${kind.replaceFirstChar(Char::uppercaseChar)} ${index + 1}",
                        summary = listOfNotNull(
                            "type=$kind",
                            node.attribute("author")?.takeIf { it.isNotBlank() }?.let { "reviewer=$it" },
                            node.attribute("date")?.takeIf { it.isNotBlank() }?.let { "timestamp=$it" },
                            node.attribute("id")?.takeIf { it.isNotBlank() }?.let { "id=$it" },
                            extractCommentReference(node)?.let { "commentRef=$it" }
                        ).joinToString(", "),
                        attributes = buildMap {
                            put("type", kind)
                            node.attribute("author")?.takeIf { it.isNotBlank() }?.let { put("reviewer", it) }
                            node.attribute("date")?.takeIf { it.isNotBlank() }?.let { put("timestamp", it) }
                            node.attribute("id")?.takeIf { it.isNotBlank() }?.let { put("id", it) }
                            extractCommentReference(node)?.let { put("commentReference", it) }
                        },
                        source = "word/document.xml"
                    )
                }
        }

        if (revisions.isEmpty()) {
            return null
        }
        return metadata(
            kind = "revisions",
            title = "Track Changes",
            summary = "${revisions.size} revisions",
            children = revisions,
            source = "word/document.xml"
        )
    }

    private fun extractReferences(document: Document, docxPackage: DocxPackage): DocumentElement.Metadata? {
        val fields = mutableListOf<MetadataInfo>()

        document.documentElement?.descendants("fldSimple").orEmpty().forEachIndexed { index, field ->
            val instruction = field.attribute("instr").orEmpty().trim()
            classifyReference(instruction)?.let { type ->
                fields += MetadataInfo(
                    kind = "reference",
                    title = "Reference ${index + 1}",
                    summary = "type=$type, instruction=$instruction",
                    attributes = mapOf("type" to type, "instruction" to instruction),
                    source = "word/document.xml"
                )
            }
        }

        val noteRefs = document.documentElement?.descendants("footnoteReference").orEmpty() +
            document.documentElement?.descendants("endnoteReference").orEmpty()
        noteRefs.forEach { ref ->
            val type = if (ref.matches("footnoteReference")) "footnote-reference" else "endnote-reference"
            fields += MetadataInfo(
                kind = "reference",
                title = type,
                summary = "type=$type, id=${ref.attribute("id").orEmpty()}",
                attributes = mapOf("type" to type, "id" to ref.attribute("id").orEmpty()),
                source = "word/document.xml"
            )
        }

        document.documentElement?.descendants("instrText").orEmpty().forEachIndexed { index, instr ->
            val instruction = instr.textValue()
            classifyReference(instruction)?.let { type ->
                fields += MetadataInfo(
                    kind = "reference",
                    title = "Instruction ${index + 1}",
                    summary = "type=$type, instruction=$instruction",
                    attributes = mapOf("type" to type, "instruction" to instruction),
                    source = "word/document.xml"
                )
            }
        }

        detectCaptions(document).forEach(fields::add)

        if (fields.isEmpty()) {
            return null
        }
        return metadata(
            kind = "references",
            title = "References",
            summary = "${fields.size} references",
            children = fields.distinctBy { "${it.kind}:${it.summary}" },
            source = "word/document.xml"
        )
    }

    private fun detectCaptions(document: Document): List<MetadataInfo> =
        document.documentElement
            ?.descendants("p")
            .orEmpty()
            .mapNotNull { paragraph ->
                val instruction = paragraph.descendants("instrText")
                    .joinToString(" ") { it.textValue() }
                    .trim()
                val type = when {
                    "SEQ Figure" in instruction -> "figure-caption"
                    "SEQ Table" in instruction -> "table-caption"
                    else -> null
                } ?: return@mapNotNull null
                val text = paragraph.descendants("t").joinToString("") { it.textValue() }
                MetadataInfo(
                    kind = "caption",
                    title = type,
                    summary = "type=$type, text=$text",
                    attributes = mapOf("type" to type, "text" to text),
                    source = "word/document.xml"
                )
            }

    private fun extractCitations(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val bibliography = docxPackage.xml("word/bibliography.xml")?.documentElement ?: return null
        val sources = bibliography.descendants("Source").mapIndexed { index, source ->
            val tag = source.firstChild("Tag")?.textValue()
            val title = source.firstChild("Title")?.textValue()
            val year = source.firstChild("Year")?.textValue()
            MetadataInfo(
                kind = "citation",
                title = tag ?: title ?: "Citation ${index + 1}",
                summary = listOfNotNull(title?.let { "title=$it" }, year?.let { "year=$it" }).joinToString(", "),
                attributes = buildMap {
                    tag?.let { put("tag", it) }
                    title?.let { put("title", it) }
                    year?.let { put("year", it) }
                },
                source = "word/bibliography.xml"
            )
        }
        if (sources.isEmpty()) {
            return null
        }
        return metadata(
            kind = "citations",
            title = "Citations",
            summary = "${sources.size} bibliography sources",
            children = sources,
            source = "word/bibliography.xml"
        )
    }

    private fun extractMath(document: Document): DocumentElement.Metadata? {
        val mathZones = document.documentElement
            ?.descendants("oMath")
            .orEmpty()
            .mapIndexed { index, math ->
                MetadataInfo(
                    kind = "math-zone",
                    title = "Equation ${index + 1}",
                    summary = listOfNotNull(
                        classifyMath(math).takeIf { it.isNotBlank() },
                        math.textValue().takeIf { it.isNotBlank() }?.take(80)
                    ).joinToString(", "),
                    attributes = mapOf("constructs" to classifyMath(math)),
                    source = "word/document.xml"
                )
            }
        if (mathZones.isEmpty()) {
            return null
        }
        return metadata(
            kind = "mathematics",
            title = "Mathematical Content",
            summary = "${mathZones.size} math zones",
            children = mathZones,
            source = "word/document.xml"
        )
    }

    private fun extractAlternateContent(document: Document, source: String = "word/document.xml"): DocumentElement.Metadata? {
        val alternates = document.documentElement
            ?.descendants("AlternateContent")
            .orEmpty()
            .mapIndexed { index, alternate ->
                val choices = alternate.children("Choice")
                val fallback = alternate.firstChild("Fallback")
                MetadataInfo(
                    kind = "alternate-content",
                    title = "Alternate Content ${index + 1}",
                    summary = "choices=${choices.size}, fallback=${fallback != null}",
                    attributes = buildMap {
                        put("choices", choices.size.toString())
                        put("hasFallback", (fallback != null).toString())
                        choices.firstOrNull()?.attribute("Requires")?.takeIf { it.isNotBlank() }?.let {
                            put("requires", it)
                        }
                    },
                    source = source
                )
            }
        if (alternates.isEmpty()) {
            return null
        }
        return metadata(
            kind = "alternate-content-set",
            title = "Alternate Content",
            summary = "${alternates.size} alternate content blocks",
            children = alternates,
            source = source
        )
    }

    private fun extractWatermark(path: String, document: Document, docxPackage: DocxPackage): DocumentElement.Metadata? {
        val textWatermarks = document.documentElement
            ?.descendants("shape")
            .orEmpty()
            .filter {
                val identifier = listOfNotNull(it.attribute("id"), it.attribute("spid"), it.attribute("type")).joinToString(" ")
                "watermark" in identifier.lowercase()
            }
        val imageWatermarks = document.documentElement?.descendants("imagedata").orEmpty()
        if (textWatermarks.isEmpty() && imageWatermarks.isEmpty()) {
            return null
        }
        val relationships = docxPackage.relationshipsFor(path)
        val children = mutableListOf<MetadataInfo>()
        textWatermarks.forEachIndexed { index, shape ->
            children += MetadataInfo(
                kind = "watermark",
                title = "Text Watermark ${index + 1}",
                summary = shape.textValue().ifBlank { "shape watermark" },
                source = path
            )
        }
        imageWatermarks.forEachIndexed { index, image ->
            val relationshipId = image.attribute("id").orEmpty()
            children += MetadataInfo(
                kind = "watermark",
                title = "Image Watermark ${index + 1}",
                summary = relationships[relationshipId] ?: relationshipId,
                attributes = mapOf("relationshipId" to relationshipId, "target" to relationships[relationshipId].orEmpty()),
                source = path
            )
        }
        return metadata(
            kind = "watermarks",
            title = "Watermarks",
            summary = "${children.size} watermarks in ${path.substringAfterLast('/')}",
            children = children,
            source = path
        )
    }

    private fun extractPageLayout(document: Document): DocumentElement.Metadata? {
        val paragraphs = document.documentElement?.descendants("p").orEmpty()
        val pages = mutableListOf<MetadataInfo>()
        var pageIndex = 1
        var sectionIndex = 0
        pages += MetadataInfo(
            kind = "page",
            title = "Page 1",
            summary = "start",
            attributes = mapOf("pageIndex" to "1"),
            source = "word/document.xml"
        )

        paragraphs.forEach { paragraph ->
            val hasExplicitPageBreak = paragraph.descendants("br").any { it.attribute("type") == "page" } ||
                paragraph.descendants("lastRenderedPageBreak").isNotEmpty()
            if (hasExplicitPageBreak) {
                pageIndex += 1
                pages += MetadataInfo(
                    kind = "page",
                    title = "Page $pageIndex",
                    summary = "explicit page break",
                    attributes = mapOf("pageIndex" to pageIndex.toString()),
                    source = "word/document.xml"
                )
            }
            paragraph.firstChild("pPr")
                ?.firstChild("sectPr")
                ?.let { section ->
                    sectionIndex += 1
                    val pageStart = section.firstChild("pgNumType")?.attribute("start")
                    pages += MetadataInfo(
                        kind = "page-section",
                        title = "Section ${sectionIndex + 1}",
                        summary = listOfNotNull(
                            "sectionBoundary=true",
                            pageStart?.let { "pageNumberStart=$it" },
                            section.firstChild("titlePg")?.let { "differentFirstPage=true" }
                        ).joinToString(", "),
                        attributes = buildMap {
                            put("sectionIndex", sectionIndex.toString())
                            pageStart?.let { put("pageNumberStart", it) }
                            if (section.firstChild("titlePg") != null) {
                                put("differentFirstPage", "true")
                            }
                        },
                        source = "word/document.xml"
                    )
                }
        }

        return metadata(
            kind = "pagination",
            title = "Page Layout",
            summary = "estimatedPages=$pageIndex",
            attributes = mapOf("estimatedPages" to pageIndex.toString()),
            children = pages,
            source = "word/document.xml"
        )
    }

    private fun extractBordersAndShading(document: Document): DocumentElement.Metadata? {
        val pageBorders = document.documentElement?.descendants("pgBorders").orEmpty()
        val paragraphBorders = document.documentElement?.descendants("pBdr").orEmpty()
        val cellBorders = document.documentElement?.descendants("tcBorders").orEmpty()
        val shading = document.documentElement?.descendants("shd").orEmpty()
        if (pageBorders.isEmpty() && paragraphBorders.isEmpty() && cellBorders.isEmpty() && shading.isEmpty()) {
            return null
        }
        return metadata(
            kind = "borders-shading",
            title = "Borders And Shading",
            summary = listOf(
                "pageBorders=${pageBorders.size}",
                "paragraphBorders=${paragraphBorders.size}",
                "cellBorders=${cellBorders.size}",
                "shading=${shading.size}"
            ).joinToString(", "),
            attributes = mapOf(
                "pageBorders" to pageBorders.size.toString(),
                "paragraphBorders" to paragraphBorders.size.toString(),
                "cellBorders" to cellBorders.size.toString(),
                "shading" to shading.size.toString()
            ),
            source = "word/document.xml"
        )
    }

    private fun extractBiDiAndComplexScript(document: Document): DocumentElement.Metadata? {
        val rtlRuns = document.documentElement?.descendants("rtl").orEmpty()
        val complexScriptFonts = document.documentElement?.descendants("rFonts").orEmpty().count {
            !it.attribute("cs").isNullOrBlank() || !it.attribute("csTheme").isNullOrBlank()
        }
        val rtlTables = document.documentElement?.descendants("bidiVisual").orEmpty()
        if (rtlRuns.isEmpty() && complexScriptFonts == 0 && rtlTables.isEmpty()) {
            return null
        }
        return metadata(
            kind = "complex-script",
            title = "Complex Script And RTL",
            summary = listOf(
                "rtlRuns=${rtlRuns.size}",
                "complexScriptFonts=$complexScriptFonts",
                "rtlTables=${rtlTables.size}"
            ).joinToString(", "),
            attributes = mapOf(
                "rtlRuns" to rtlRuns.size.toString(),
                "complexScriptFonts" to complexScriptFonts.toString(),
                "rtlTables" to rtlTables.size.toString()
            ),
            source = "word/document.xml"
        )
    }

    private fun resolveContentControlType(properties: Element?): String {
        if (properties == null) {
            return "unknown"
        }
        return when {
            properties.firstChild("text") != null -> "plain-text"
            properties.firstChild("richText") != null -> "rich-text"
            properties.firstChild("dropDownList") != null -> "drop-down-list"
            properties.firstChild("checkBox") != null -> "checkbox"
            properties.firstChild("date") != null -> "date-picker"
            properties.firstChild("comboBox") != null -> "combo-box"
            properties.firstChild("docPartList") != null -> "building-block-gallery"
            else -> "unknown"
        }
    }

    private fun extractCommentReference(node: Element): String? =
        node.descendants("commentReference").firstOrNull()?.attribute("id")

    private fun classifyReference(instruction: String): String? {
        val normalized = instruction.trim().uppercase()
        return when {
            normalized.startsWith("REF ") -> "cross-reference"
            normalized.startsWith("PAGEREF ") -> "page-reference"
            normalized.startsWith("NOTEREF ") -> "note-reference"
            normalized.startsWith("SEQ ") -> "caption-sequence"
            normalized.startsWith("CITATION ") -> "citation"
            normalized.startsWith("BIBLIOGRAPHY") -> "bibliography"
            normalized.startsWith("HYPERLINK ") -> "hyperlink-field"
            normalized.startsWith("IF ") -> "conditional-field"
            normalized.startsWith("MERGEFIELD ") -> "merge-field"
            normalized.startsWith("TOC") -> "table-of-contents"
            normalized.startsWith("PAGE") -> "page-number"
            else -> null
        }
    }

    private fun classifyMath(math: Element): String =
        listOfNotNull(
            math.descendants("f").takeIf { it.isNotEmpty() }?.let { "fractions=${it.size}" },
            math.descendants("rad").takeIf { it.isNotEmpty() }?.let { "radicals=${it.size}" },
            math.descendants("sSub").takeIf { it.isNotEmpty() }?.let { "subscripts=${it.size}" },
            math.descendants("sSup").takeIf { it.isNotEmpty() }?.let { "superscripts=${it.size}" },
            math.descendants("nary").takeIf { it.isNotEmpty() }?.let { "integralsOrNary=${it.size}" },
            math.descendants("m").takeIf { it.isNotEmpty() }?.let { "matrices=${it.size}" }
        ).joinToString(", ")

    private fun metadata(
        kind: String,
        title: String,
        summary: String,
        attributes: Map<String, String> = emptyMap(),
        children: List<MetadataInfo> = emptyList(),
        source: String
    ): DocumentElement.Metadata = DocumentElement.Metadata(
        MetadataInfo(
            kind = kind,
            title = title,
            summary = summary,
            attributes = attributes.filterValues { it.isNotBlank() },
            children = children,
            source = source
        )
    )
}
