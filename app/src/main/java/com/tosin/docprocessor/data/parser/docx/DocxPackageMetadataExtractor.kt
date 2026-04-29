package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.MetadataInfo
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.w3c.dom.Element

class DocxPackageMetadataExtractor : DocxPackageExtractor {

    override fun extract(document: XWPFDocument, docxPackage: DocxPackage): List<DocumentElement> {
        val output = mutableListOf<DocumentElement>()
        parseCoreProperties(docxPackage)?.let(output::add)
        parseExtendedProperties(docxPackage)?.let(output::add)
        parseCustomProperties(docxPackage)?.let(output::add)
        parseSettings(docxPackage)?.let(output::add)
        parseFonts(docxPackage)?.let(output::add)
        parseStyles(docxPackage)?.let(output::add)
        parseBackground(docxPackage)?.let(output::add)
        return output
    }

    private fun parseCoreProperties(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("docProps/core.xml")?.documentElement ?: return null
        val attributes = linkedMapOf<String, String>()
        mapOf(
            "title" to "title",
            "subject" to "subject",
            "creator" to "author",
            "keywords" to "keywords",
            "lastModifiedBy" to "lastModifiedBy",
            "revision" to "revision",
            "created" to "created",
            "modified" to "modified",
            "description" to "description"
        ).forEach { (elementName, label) ->
            root.firstChild(elementName)?.textValue()?.takeIf { it.isNotBlank() }?.let {
                attributes[label] = it
            }
        }
        if (attributes.isEmpty()) {
            return null
        }
        return metadata(
            kind = "document-properties-core",
            title = "Core Properties",
            summary = listOfNotNull(
                attributes["title"]?.let { "title=$it" },
                attributes["author"]?.let { "author=$it" },
                attributes["lastModifiedBy"]?.let { "lastModifiedBy=$it" },
                attributes["revision"]?.let { "revision=$it" }
            ).joinToString(", ").ifBlank { "Core document properties" },
            attributes = attributes,
            source = "docProps/core.xml"
        )
    }

    private fun parseExtendedProperties(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("docProps/app.xml")?.documentElement ?: return null
        val attributes = linkedMapOf<String, String>()
        listOf("Pages", "Words", "Characters", "Lines", "Paragraphs", "Company", "Template", "TotalTime")
            .forEach { name ->
                root.firstChild(name)?.textValue()?.takeIf { it.isNotBlank() }?.let { value ->
                    attributes[name.replaceFirstChar(Char::lowercaseChar)] = value
                }
            }
        if (attributes.isEmpty()) {
            return null
        }
        return metadata(
            kind = "document-properties-extended",
            title = "Extended Properties",
            summary = listOfNotNull(
                attributes["pages"]?.let { "pages=$it" },
                attributes["words"]?.let { "words=$it" },
                attributes["characters"]?.let { "characters=$it" },
                attributes["paragraphs"]?.let { "paragraphs=$it" }
            ).joinToString(", ").ifBlank { "Extended document properties" },
            attributes = attributes,
            source = "docProps/app.xml"
        )
    }

    private fun parseCustomProperties(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("docProps/custom.xml")?.documentElement ?: return null
        val children = root.children("property").mapNotNull { property ->
            val name = property.attribute("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val valueNode = property.children().firstOrNull() ?: return@mapNotNull null
            MetadataInfo(
                kind = "custom-property",
                title = name,
                summary = "$name=${valueNode.textValue()}",
                attributes = mapOf("name" to name, "value" to valueNode.textValue()),
                source = "docProps/custom.xml"
            )
        }
        if (children.isEmpty()) {
            return null
        }
        return metadata(
            kind = "document-properties-custom",
            title = "Custom Properties",
            summary = "${children.size} custom properties",
            children = children,
            source = "docProps/custom.xml"
        )
    }

    private fun parseSettings(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("word/settings.xml")?.documentElement ?: return null
        val settings = linkedMapOf<String, String>()

        root.firstChild("documentProtection")?.let { protection ->
            settings["documentProtection.edit"] = protection.attribute("edit").orEmpty()
            settings["documentProtection.enforcement"] = protection.attribute("enforcement").orEmpty()
            protection.attribute("formatting")?.takeIf { it.isNotBlank() }?.let {
                settings["documentProtection.formatting"] = it
            }
        }
        root.firstChild("trackRevisions")?.let { settings["trackRevisions"] = "true" }
        root.firstChild("evenAndOddHeaders")?.let { settings["differentOddEvenHeaders"] = "true" }
        root.firstChild("doNotHyphenateCaps")?.let { settings["doNotHyphenateCaps"] = "true" }
        root.firstChild("hyphenationZone")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
            settings["hyphenationZone"] = it
        }
        root.firstChild("defaultTabStop")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
            settings["defaultTabStop"] = it
        }
        root.firstChild("zoom")?.attribute("percent")?.takeIf { it.isNotBlank() }?.let {
            settings["zoomPercent"] = it
        }
        if (settings.isEmpty()) {
            return null
        }
        return metadata(
            kind = "document-settings",
            title = "Document Settings",
            summary = listOfNotNull(
                settings["documentProtection.edit"]?.takeIf { it.isNotBlank() }?.let { "protection=$it" },
                settings["trackRevisions"]?.let { "trackRevisions=$it" },
                settings["differentOddEvenHeaders"]?.let { "oddEvenHeaders=$it" },
                settings["hyphenationZone"]?.let { "hyphenationZone=$it" }
            ).joinToString(", ").ifBlank { "Document settings" },
            attributes = settings,
            source = "word/settings.xml"
        )
    }

    private fun parseFonts(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("word/fontTable.xml")?.documentElement ?: return null
        val fonts = root.children("font").mapNotNull { font ->
            val name = font.attribute("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MetadataInfo(
                kind = "font",
                title = name,
                summary = buildString {
                    append(name)
                    font.firstChild("altName")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        append(", alt=")
                        append(it)
                    }
                    font.firstChild("family")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        append(", family=")
                        append(it)
                    }
                    if (font.firstChild("embedRegular") != null) append(", embeddedRegular")
                    if (font.firstChild("embedBold") != null) append(", embeddedBold")
                    if (font.firstChild("embedItalic") != null) append(", embeddedItalic")
                    if (font.firstChild("embedBoldItalic") != null) append(", embeddedBoldItalic")
                },
                attributes = buildMap {
                    put("fontFace", name)
                    font.firstChild("altName")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        put("alternateFont", it)
                    }
                    font.firstChild("family")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        put("family", it)
                    }
                },
                source = "word/fontTable.xml"
            )
        }
        if (fonts.isEmpty()) {
            return null
        }
        return metadata(
            kind = "embedded-fonts",
            title = "Embedded Fonts",
            summary = "${fonts.size} fonts declared",
            children = fonts,
            source = "word/fontTable.xml"
        )
    }

    private fun parseStyles(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val root = docxPackage.xml("word/styles.xml")?.documentElement ?: return null
        val styles = root.children("style").mapNotNull { style ->
            val styleId = style.attribute("styleId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val type = style.attribute("type").orEmpty().ifBlank { "unknown" }
            val name = style.firstChild("name")?.attribute("val").orEmpty().ifBlank { styleId }
            MetadataInfo(
                kind = "style",
                title = name,
                summary = listOfNotNull(
                    "type=$type",
                    "styleId=$styleId",
                    style.attribute("default")?.takeIf { it.isNotBlank() }?.let { "default=$it" },
                    style.firstChild("link")?.attribute("val")?.takeIf { it.isNotBlank() }?.let { "linked=$it" },
                    style.firstChild("basedOn")?.attribute("val")?.takeIf { it.isNotBlank() }?.let { "basedOn=$it" }
                ).joinToString(", "),
                attributes = buildMap {
                    put("type", type)
                    put("styleId", styleId)
                    put("name", name)
                    style.attribute("default")?.takeIf { it.isNotBlank() }?.let { put("default", it) }
                    style.firstChild("link")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        put("linkedStyle", it)
                    }
                    style.firstChild("basedOn")?.attribute("val")?.takeIf { it.isNotBlank() }?.let {
                        put("basedOn", it)
                    }
                },
                source = "word/styles.xml"
            )
        }

        val docDefaults = root.firstChild("docDefaults")
        val defaultChildren = buildList {
            docDefaults?.firstChild("rPrDefault")
                ?.descendants()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    add(
                        MetadataInfo(
                            kind = "style-default-run",
                            title = "Default Run Style",
                            summary = it.joinToString(", ") { node -> node.nodeName.substringAfter(':') },
                            source = "word/styles.xml"
                        )
                    )
                }
        }

        if (styles.isEmpty() && defaultChildren.isEmpty()) {
            return null
        }

        return metadata(
            kind = "styles",
            title = "Styles",
            summary = "${styles.size} styles parsed",
            children = styles + defaultChildren,
            source = "word/styles.xml"
        )
    }

    private fun parseBackground(docxPackage: DocxPackage): DocumentElement.Metadata? {
        val documentRoot = docxPackage.xml("word/document.xml")?.documentElement ?: return null
        val background = documentRoot.firstChild("background") ?: return null
        val documentRelationships = docxPackage.relationshipsFor("word/document.xml")
        val imageId = background.attribute("id")
            ?: background.attribute("embed")
            ?: background.attribute("link")
        val attributes = linkedMapOf<String, String>()
        background.attribute("color")?.takeIf { it.isNotBlank() }?.let { attributes["color"] = it }
        background.attribute("themeColor")?.takeIf { it.isNotBlank() }?.let { attributes["themeColor"] = it }
        imageId?.let { id ->
            documentRelationships[id]?.let { attributes["image"] = it }
        }
        if (attributes.isEmpty()) {
            return null
        }
        return metadata(
            kind = "background",
            title = "Page Background",
            summary = listOfNotNull(
                attributes["color"]?.let { "color=$it" },
                attributes["image"]?.let { "image=$it" }
            ).joinToString(", ").ifBlank { "Document background" },
            attributes = attributes,
            source = "word/document.xml"
        )
    }

    private fun metadata(
        kind: String,
        title: String,
        summary: String,
        attributes: Map<String, String> = emptyMap(),
        children: List<MetadataInfo> = emptyList(),
        source: String
    ): DocumentElement.Metadata = DocumentElement.Metadata(
        info = MetadataInfo(
            kind = kind,
            title = title,
            summary = summary,
            attributes = attributes.filterValues { it.isNotBlank() },
            children = children,
            source = source
        )
    )

    private fun Element.firstChild(name: String): Element? = children(name).firstOrNull()
}
