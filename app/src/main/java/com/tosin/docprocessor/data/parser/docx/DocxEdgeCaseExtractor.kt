package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Extractor for the "other 5%" of DOCX elements: edge cases, specialized content,
 * and advanced markup that standard parsers often skip.
 *
 * Covers: Document Protection, Custom XML, Mail Merge, Interactive Content,
 * Embedded Files, and complex DrawingML state.
 */
class DocxEdgeCaseExtractor : DocxPackageExtractor {

    override fun extract(document: XWPFDocument, docxPackage: DocxPackage): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()

        // 1. Document Protection & Rights Management (Part 8)
        extractProtection(docxPackage)?.let(output::add)

        // 2. Custom XML & Third-Party Extensions (Part 12)
        extractCustomXml(docxPackage)?.let(output::add)

        // 3. Mail Merge & Data Sources (Part 15)
        extractMailMerge(docxPackage)?.let(output::add)

        // 4. Interactive, Macros & Digital Signatures (Part 16)
        extractInteractiveContent(docxPackage)?.let(output::add)

        // 5. Embedded & Linked Files (Part 3)
        extractEmbeddedFiles(docxPackage)?.let(output::add)

        // 6. Accessibility & Alt Text Metadata (Part 14)
        extractAccessibilityMetadata(docxPackage)?.let(output::add)

        // 7. Hyphenation & Justification Settings (Part 10)
        extractHyphenation(docxPackage)?.let(output::add)

        // 8. Placeholder & Inactive Content (Part 11)
        extractPlaceholders(docxPackage)?.let(output::add)

        return output
    }

    private fun extractProtection(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val settings = docxPackage.xml("word/settings.xml") ?: return null
        val root = settings.documentElement
        
        val protection = root.firstChild("documentProtection")
        val writeProtection = root.firstChild("writeProtection")
        val readOnly = root.firstChild("readOnlyRecommended")

        if (protection == null && writeProtection == null && readOnly == null) return null

        val info = MetadataInfo(
            kind = "protection",
            title = "Document Protection",
            summary = buildString {
                if (protection != null) append("Enforced: ${protection.attribute("edit") ?: "restricted"}; ")
                if (writeProtection != null) append("Write-Protected; ")
                if (readOnly != null) append("Read-Only Recommended; ")
            }.trim().removeSuffix(";"),
            attributes = buildMap {
                protection?.let {
                    put("editType", it.attribute("edit") ?: "unknown")
                    put("enforcement", it.attribute("enforcement") ?: "1")
                    it.attribute("cryptProviderType")?.let { p -> put("provider", p) }
                }
                if (writeProtection != null) put("writeProtected", "true")
                if (readOnly != null) put("readOnlyRecommended", "true")
            },
            source = "word/settings.xml"
        )
        return DocumentElement.Metadata(info = info)
    }

    private fun extractCustomXml(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val itemPaths = docxPackage.paths("customXml/", ".xml")
        if (itemPaths.isEmpty()) return null

        val children = itemPaths.map { path ->
            val xml = docxPackage.xml(path)
            val rootName = xml?.documentElement?.nodeName ?: "unknown"
            val namespace = xml?.documentElement?.namespaceURI ?: ""
            MetadataInfo(
                kind = "custom-xml",
                title = path.substringAfterLast('/'),
                summary = "root=$rootName, ns=$namespace",
                attributes = mapOf("path" to path, "namespace" to namespace),
                source = path
            )
        }

        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = "custom-xml-collection",
                title = "Custom XML Data",
                summary = "${children.size} data parts",
                children = children,
                source = "customXml/"
            )
        )
    }

    private fun extractMailMerge(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val settings = docxPackage.xml("word/settings.xml") ?: return null
        val mailMerge = settings.documentElement.firstChild("mailMerge") ?: return null

        val type = mailMerge.firstChild("mainDocumentType")?.attribute("val") ?: "unknown"
        val dataType = mailMerge.firstChild("dataType")?.attribute("val") ?: "none"
        val dataSource = mailMerge.firstChild("dataSource")?.attribute("val") ?: ""

        val info = MetadataInfo(
            kind = "mail-merge",
            title = "Mail Merge Configuration",
            summary = "type=$type, dataSource=${dataSource.substringAfterLast('\\')}",
            attributes = buildMap {
                put("type", type)
                put("dataType", dataType)
                put("dataSource", dataSource)
                mailMerge.firstChild("query")?.attribute("val")?.let { put("query", it) }
            },
            source = "word/settings.xml"
        )
        return DocumentElement.Metadata(info = info)
    }

    private fun extractInteractiveContent(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val children = mutableListOf<MetadataInfo>()

        // Macros (VBA)
        if (docxPackage.paths("word/", ".bin").any { "vbaProject" in it }) {
            children += MetadataInfo(
                kind = "macro",
                title = "VBA Macros",
                summary = "Document contains macro-enabled content (vbaProject.bin)",
                attributes = mapOf("file" to "word/vbaProject.bin"),
                source = "word/vbaProject.bin"
            )
        }

        // Digital Signatures
        val sigs = docxPackage.paths("_xmlsignatures/", ".xml")
        if (sigs.isNotEmpty()) {
            children += MetadataInfo(
                kind = "signature",
                title = "Digital Signatures",
                summary = "${sigs.size} signatures found",
                attributes = mapOf("count" to sigs.size.toString()),
                source = "_xmlsignatures/"
            )
        }

        // ActiveX Controls
        val activeX = docxPackage.paths("word/activeX/", ".xml")
        if (activeX.isNotEmpty()) {
            children += MetadataInfo(
                kind = "activex",
                title = "ActiveX Controls",
                summary = "${activeX.size} interactive controls",
                source = "word/activeX/"
            )
        }

        if (children.isEmpty()) return null
        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = "interactive-content",
                title = "Interactive & Advanced Content",
                summary = "Macros, Signatures, or ActiveX found",
                children = children
            )
        )
    }

    private fun extractEmbeddedFiles(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val embeddings = docxPackage.paths("word/embeddings/")
        if (embeddings.isEmpty()) return null

        val children = embeddings.map { path ->
            val extension = path.substringAfterLast('.', "bin")
            MetadataInfo(
                kind = "embedded-file",
                title = path.substringAfterLast('/'),
                summary = "type=$extension",
                attributes = mapOf("path" to path, "extension" to extension),
                source = path
            )
        }

        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = "embeddings-collection",
                title = "Embedded Objects & Files",
                summary = "${children.size} embedded items",
                children = children,
                source = "word/embeddings/"
            )
        )
    }

    private fun extractAccessibilityMetadata(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val doc = docxPackage.xml("word/document.xml") ?: return null
        val drawings = doc.documentElement.descendants("docPr")
        
        val accessibilityNodes = drawings.filter { 
            it.attribute("descr") != null || it.attribute("title") != null || it.firstChild("attr")?.attribute("decorative") == "1"
        }

        if (accessibilityNodes.isEmpty()) return null

        val children = accessibilityNodes.mapIndexed { index, node ->
            MetadataInfo(
                kind = "accessibility",
                title = "Alt Text ${index + 1}",
                summary = node.attribute("descr") ?: node.attribute("title") ?: "Decorative object",
                attributes = buildMap {
                    node.attribute("title")?.let { put("title", it) }
                    node.attribute("descr")?.let { put("description", it) }
                    if (node.firstChild("attr")?.attribute("decorative") == "1") {
                        put("decorative", "true")
                    }
                },
                source = "word/document.xml"
            )
        }

        return DocumentElement.Metadata(
            info = MetadataInfo(
                kind = "accessibility-data",
                title = "Accessibility Metadata",
                summary = "${children.size} objects with alt text",
                children = children,
                source = "word/document.xml"
            )
        )
    }

    private fun extractHyphenation(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val settings = docxPackage.xml("word/settings.xml") ?: return null
        val root = settings.documentElement
        
        val autoHyphenation = root.firstChild("autoHyphenation")
        val hyphenationZone = root.firstChild("hyphenationZone")
        val consecutiveHyphenLimit = root.firstChild("consecutiveHyphenLimit")

        if (autoHyphenation == null && hyphenationZone == null) return null

        val info = MetadataInfo(
            kind = "hyphenation",
            title = "Hyphenation Settings",
            summary = "auto=${autoHyphenation?.attribute("val") ?: "false"}",
            attributes = buildMap {
                put("auto", (autoHyphenation?.attribute("val") ?: "false"))
                hyphenationZone?.attribute("val")?.let { put("zone", it) }
                consecutiveHyphenLimit?.attribute("val")?.let { put("limit", it) }
            },
            source = "word/settings.xml"
        )
        return DocumentElement.Metadata(info = info)
    }

    private fun extractPlaceholders(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val doc = docxPackage.xml("word/document.xml") ?: return null
        val placeholders = doc.documentElement.descendants("showingPlcHdr")
        
        if (placeholders.isEmpty()) return null

        val info = MetadataInfo(
            kind = "placeholders",
            title = "Inactive Content",
            summary = "${placeholders.size} active placeholders detected",
            attributes = mapOf("count" to placeholders.size.toString()),
            source = "word/document.xml"
        )
        return DocumentElement.Metadata(info = info)
    }
}
