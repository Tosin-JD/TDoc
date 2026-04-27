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

    fun parseParagraph(element: Element): DocumentElement.Paragraph {
        val spans = mutableListOf<TextSpan>()
        collectSpans(element, spans)
        
        val styleName = element.getAttributeNS(textNs, "style-name")
        val properties = styles[styleName]
        
        return DocumentElement.Paragraph(
            spans = spans,
            listLabel = null,
            style = ParagraphStyle(
                alignment = ParagraphAlignment.START,
                indentation = ParagraphIndentation(),
                spacing = ParagraphSpacing()
            ),
            hyperlink = null,
            listInfo = null
        )
    }

    fun parseHeader(element: Element): DocumentElement.SectionHeader {
        val text = element.textContent.orEmpty().trim()
        val level = element.getAttributeNS(textNs, "outline-level").toIntOrNull() ?: 1
        return DocumentElement.SectionHeader(text, level)
    }

    private fun collectSpans(node: Node, output: MutableList<TextSpan>) {
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
                        else -> collectSpans(element, output)
                    }
                }
            }
        }
    }
}
