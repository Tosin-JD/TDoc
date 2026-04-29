package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.util.TDocLogger
import org.w3c.dom.Element
import org.w3c.dom.Node

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing

class OdtParagraphParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties>
) {

    private val textNs = OdtNamespaces.TEXT
    private val xlinkNs = OdtNamespaces.XLINK

    fun parseParagraph(element: Element, listInfo: com.tosin.docprocessor.data.parser.internal.models.ListInfo? = null): List<DocumentElement> {
        val spans = mutableListOf<TextSpan>()
        val extraElements = mutableListOf<DocumentElement>()
        collectSpans(element, spans, extraElements)
        
        val styleName = element.getAttributeNS(textNs, "style-name")
        val properties = getStyleOrDefault(styleName, "paragraph")
        val hyperlinkTargets = element.getElementsByTagNameNS(textNs, "a")
        val paragraphHyperlink = (0 until hyperlinkTargets.length)
            .asSequence()
            .mapNotNull { index -> (hyperlinkTargets.item(index) as? Element)?.getAttributeNS(xlinkNs, "href")?.takeIf { it.isNotBlank() } }
            .firstOrNull()
        
        val paragraph = DocumentElement.Paragraph(
            spans = spans,
            listLabel = null,
            style = ParagraphStyle(
                alignment = when (properties?.alignment) {
                    "center" -> ParagraphAlignment.CENTER
                    "end", "right" -> ParagraphAlignment.END
                    "justify" -> ParagraphAlignment.JUSTIFIED
                    else -> ParagraphAlignment.START
                },
                indentation = ParagraphIndentation(),
                spacing = ParagraphSpacing()
            ),
            hyperlink = paragraphHyperlink?.let { HyperlinkInfo(address = it) },
            listInfo = listInfo
        )
        
        return listOf(paragraph) + extraElements
    }

    fun parseHeader(element: Element): DocumentElement.SectionHeader {
        val text = element.textContent.orEmpty().trim()
        val level = element.getAttributeNS(textNs, "outline-level").toIntOrNull() ?: 1
        return DocumentElement.SectionHeader(text = text, level = level)
    }

    private fun collectSpans(node: Node, output: MutableList<TextSpan>, extraElements: MutableList<DocumentElement>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrBlank()) {
                        output += TextSpan(text = text)
                    }
                }
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    when (element.localName) {
                        "span" -> {
                            val styleName = element.getAttributeNS(textNs, "style-name")
                            val style = getStyleOrDefault(styleName, "span")
                            output += TextSpan(
                                text = element.textContent,
                                isBold = style.isBold,
                                isItalic = style.isItalic,
                                isUnderline = style.isUnderline,
                                color = style.color ?: "000000",
                                fontSize = style.fontSize?.toInt() ?: 12
                            )
                        }
                        "s" -> {
                            val count = element.getAttributeNS(textNs, "c").toIntOrNull() ?: 1
                            output += TextSpan(text = " ".repeat(count))
                        }
                        "tab" -> output += TextSpan(text = "\t")
                        "line-break" -> output += TextSpan(text = "\n")
                        "a" -> {
                            val href = element.getAttributeNS(xlinkNs, "href")
                            output += TextSpan(
                                text = element.textContent,
                                color = "0000EE",
                                isUnderline = true
                            )
                            if (href.isBlank()) {
                                TDocLogger.warn("ODT hyperlink element is missing href")
                            }
                        }
                        "footnote", "endnote" -> {
                            val kind = if (element.localName == "footnote") 
                                com.tosin.docprocessor.data.parser.internal.models.NoteKind.FOOTNOTE 
                            else 
                                com.tosin.docprocessor.data.parser.internal.models.NoteKind.ENDNOTE
                            
                            val body = element.getElementsByTagNameNS(textNs, "${element.localName}-body").item(0) as? Element
                            val text = body?.textContent?.trim().orEmpty()
                            extraElements += DocumentElement.Note(
                                info = com.tosin.docprocessor.data.parser.internal.models.NoteInfo(
                                    kind = kind,
                                    id = "odt_${System.currentTimeMillis()}",
                                    text = text
                                )
                            )
                        }
                        "annotation" -> {
                            val author = element.getElementsByTagNameNS(OdtNamespaces.DC, "creator").item(0)?.textContent?.trim()
                            val date = element.getElementsByTagNameNS(OdtNamespaces.DC, "date").item(0)?.textContent?.trim()
                            val text = element.textContent.trim()
                            extraElements += DocumentElement.Comment(
                                info = com.tosin.docprocessor.data.parser.internal.models.CommentInfo(
                                    id = "odt_${System.currentTimeMillis()}",
                                    author = author,
                                    date = date,
                                    text = text
                                )
                            )
                        }
                        else -> collectSpans(element, output, extraElements)
                    }
                }
            }
        }
    }

    private fun getStyleOrDefault(styleName: String?, context: String): OdtStyleParser.StyleProperties {
        val normalizedStyleName = styleName?.takeIf { it.isNotBlank() }
        if (normalizedStyleName == null) {
            return OdtStyleParser.StyleProperties.DEFAULT
        }
        return styles[normalizedStyleName] ?: run {
            TDocLogger.warn("ODT $context references missing style '$normalizedStyleName'; using defaults")
            OdtStyleParser.StyleProperties.DEFAULT
        }
    }
}
