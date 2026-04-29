package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.w3c.dom.Element
import org.w3c.dom.Node

import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing

class OdtParagraphParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties>
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
            hyperlink = null,
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
                            val style = styles[styleName]
                            output += TextSpan(
                                text = element.textContent,
                                isBold = style?.isBold ?: false,
                                isItalic = style?.isItalic ?: false,
                                isUnderline = style?.isUnderline ?: false,
                                color = style?.color ?: "000000",
                                fontSize = style?.fontSize?.toInt() ?: 12
                            )
                        }
                        "s" -> {
                            val count = element.getAttributeNS(textNs, "c").toIntOrNull() ?: 1
                            output += TextSpan(text = " ".repeat(count))
                        }
                        "tab" -> output += TextSpan(text = "\t")
                        "line-break" -> output += TextSpan(text = "\n")
                        "a" -> {
                            // Hyperlink
                            val href = element.getAttributeNS(xlinkNs, "href")
                            output += TextSpan(
                                text = element.textContent,
                                color = "0000EE",
                                isUnderline = true
                                // We could add hyperlink info to TextSpan if it supported it
                            )
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
                            val author = element.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator").item(0)?.textContent
                            val text = element.textContent.trim()
                            extraElements += DocumentElement.Comment(
                                info = com.tosin.docprocessor.data.parser.internal.models.CommentInfo(
                                    id = "odt_${System.currentTimeMillis()}",
                                    author = author,
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
}
