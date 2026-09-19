package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OdtXmlParser(
    private val cacheDir: File,
    private val zipEntries: Map<String, ByteArray>
) {

    private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val drawNs = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"

    private lateinit var styles: MutableMap<String, OdtStyleParser.StyleProperties>
    private lateinit var paragraphParser: OdtParagraphParser
    private lateinit var tableParser: OdtTableParser
    private lateinit var imageParser: OdtImageParser
    private lateinit var subParser: OdtSubElementParser

    fun parse(contentXmlBytes: ByteArray, stylesXmlBytes: ByteArray? = null): List<DocumentElement> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(contentXmlBytes))
        val root = doc.documentElement

        styles = OdtStyleParser().parseStyles(root).toMutableMap()
        if (stylesXmlBytes != null) {
            val stylesDoc = factory.newDocumentBuilder().parse(ByteArrayInputStream(stylesXmlBytes))
            val stylesRoot = stylesDoc.documentElement
            val declared = OdtStyleParser().parseStyles(stylesRoot)
            declared.forEach { (name, props) ->
                // Automatic styles in content.xml take precedence; declared
                // styles in styles.xml fill in the gaps.
                if (!styles.containsKey(name)) {
                    styles[name] = props
                }
            }
        }

        paragraphParser = OdtParagraphParser(styles)
        tableParser = OdtTableParser(styles, paragraphParser)
        imageParser = OdtImageParser(cacheDir, zipEntries)
        subParser = OdtSubElementParser()

        val elements = mutableListOf<DocumentElement>()

        // Find office:body/office:text
        val bodyNodes = root.getElementsByTagNameNS(officeNs, "body")
        if (bodyNodes.length > 0) {
            val body = bodyNodes.item(0) as Element
            val textNodes = body.getElementsByTagNameNS(officeNs, "text")
            if (textNodes.length > 0) {
                traverse(textNodes.item(0), elements)
            }
        }

        return elements
    }

    private fun traverse(node: Node, output: MutableList<DocumentElement>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                when (child.localName) {
                    "h" -> output += paragraphParser.parseHeader(element)
                    "p" -> output += paragraphParser.parseParagraph(element)
                    "table" -> output += tableParser.parseTable(element)
                    "frame" -> imageParser.parseImage(element)?.let { output += it }
                    "image" -> imageParser.parseImage(element)?.let { output += it }
                    "section" -> traverse(child, output) // Section content is transparent for MVP
                    "list" -> traverse(child, output)    // List items parsed as their paragraphs
                    else -> {
                        val subElements = subParser.tryParse(element)
                        if (subElements == null) {
                            traverse(child, output)
                        } else {
                            output += subElements
                        }
                    }
                }
            }
        }
    }
}