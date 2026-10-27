package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.internal.models.CommentInfo
import com.tosin.docprocessor.data.parser.internal.models.NoteInfo
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.w3c.dom.Element
import org.w3c.dom.Node

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing

class OdtParagraphParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties>,
    private val imageParser: OdtImageParser
) {

    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val xlinkNs = "http://www.w3.org/1999/xlink"

    fun parseParagraph(element: Element, listInfo: com.tosin.docprocessor.data.parser.internal.models.ListInfo? = null): List<DocumentElement> {
        val spans = mutableListOf<TextSpan>()
        val extraElements = mutableListOf<DocumentElement>()
        collectSpans(element, spans, extraElements)
        
        val styleName = element.getAttributeNS(textNs, "style-name")
        val properties = styles[styleName]
        
        val paragraph = DocumentElement.Paragraph(
            spans = spans,
            listLabel = listItemLabel(element),
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
            hyperlink = null,
            listInfo = listInfo
        )
        
        return listOf(paragraph) + extraElements
    }

    private fun listItemLabel(paragraph: Element): String? {
        val listItem = paragraph.parentNode as? Element ?: return null
        if (listItem.localName != "list-item" || listItem.namespaceURI != textNs) return null
        return (0 until listItem.childNodes.length)
            .asSequence()
            .mapNotNull { listItem.childNodes.item(it) as? Element }
            .firstOrNull { it.localName == "list-label" && it.namespaceURI == textNs }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun parseHeader(element: Element): DocumentElement.SectionHeader {
        val text = element.textContent.orEmpty().trim()
        val level = element.getAttributeNS(textNs, "outline-level").toIntOrNull() ?: 1
        return DocumentElement.SectionHeader(text = text, level = level)
    }

    private fun collectSpans(
        node: Node,
        output: MutableList<TextSpan>,
        extraElements: MutableList<DocumentElement>,
        inheritedStyle: OdtStyleParser.StyleProperties? = null,
        hyperlink: HyperlinkInfo? = null
    ) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrEmpty()) {
                        output += styledSpan(text, inheritedStyle, hyperlink)
                    }
                }
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    when (element.localName) {
                        "span" -> {
                            val styleName = element.getAttributeNS(textNs, "style-name")
                            collectSpans(element, output, extraElements, styles[styleName] ?: inheritedStyle, hyperlink)
                        }
                        "s" -> {
                            val count = element.getAttributeNS(textNs, "c").toIntOrNull() ?: 1
                            output += styledSpan(" ".repeat(count), inheritedStyle, hyperlink)
                        }
                        "tab" -> output += styledSpan("\t", inheritedStyle, hyperlink)
                        "line-break" -> output += styledSpan("\n", inheritedStyle, hyperlink)
                        "a" -> {
                            val href = element.getAttributeNS(xlinkNs, "href")
                            val link = HyperlinkInfo(
                                address = href.takeIf { it.isNotBlank() },
                                tooltip = element.getAttributeNS(xlinkNs, "title").takeIf { it.isNotBlank() }
                            )
                            collectSpans(element, output, extraElements, inheritedStyle, link)
                        }
                        "footnote", "endnote" -> {
                            val kind = if (element.localName == "footnote") 
                                com.tosin.docprocessor.data.parser.internal.models.NoteKind.FOOTNOTE 
                            else 
                                com.tosin.docprocessor.data.parser.internal.models.NoteKind.ENDNOTE
                            
                            val body = element.getElementsByTagNameNS(textNs, "${element.localName}-body").item(0) as? Element
                            val text = body?.textContent?.trim().orEmpty()
                            extraElements += DocumentElement.Note(
                                info = NoteInfo(
                                    kind = kind,
                                    id = "odt_${System.currentTimeMillis()}",
                                    text = text
                                )
                            )
                        }
                        "annotation" -> {
                            val author = element.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator").item(0)?.textContent
                            val text = element.textContent.trim()
                            extraElements += DocumentElement.Comment(
                                info = CommentInfo(
                                    id = "odt_${System.currentTimeMillis()}",
                                    author = author,
                                    text = text
                                )
                            )
                        }
                        "frame" -> imageParser.parseImage(element)?.let(extraElements::add)
                        else -> collectSpans(element, output, extraElements, inheritedStyle, hyperlink)
                    }
                }
            }
        }
    }

    private fun styledSpan(
        text: String,
        style: OdtStyleParser.StyleProperties?,
        hyperlink: HyperlinkInfo?
    ): TextSpan = TextSpan(
        text = text,
        isBold = style?.isBold ?: false,
        isItalic = style?.isItalic ?: false,
        isUnderline = (style?.isUnderline ?: false) || hyperlink != null,
        color = style?.color ?: if (hyperlink != null) "0000EE" else "000000",
        fontSize = style?.fontSize,
        hyperlink = hyperlink
    )
}
