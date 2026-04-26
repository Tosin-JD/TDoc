package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class OdtXmlParser {

    fun parse(xml: String): List<DocumentElement> {
        val builderFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = builderFactory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val elements = mutableListOf<DocumentElement>()
        traverse(document.documentElement, elements)
        return elements
    }

    private fun traverse(node: Node, output: MutableList<DocumentElement>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            when (element.localName) {
                "h" -> {
                    val text = element.textContent?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val level = element.getAttribute("text:outline-level").toIntOrNull() ?: 1
                        output += DocumentElement.SectionHeader(text = text, level = level)
                    }
                }
                "p" -> {
                    val text = element.textContent?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        output += DocumentElement.Paragraph(
                            spans = listOf(TextSpan(text = text, color = "000000"))
                        )
                    }
                }
            }
        }

        val children = node.childNodes
        for (index in 0 until children.length) {
            traverse(children.item(index), output)
        }
    }
}
