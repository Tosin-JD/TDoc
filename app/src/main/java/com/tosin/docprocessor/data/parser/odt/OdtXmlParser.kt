package com.tosin.docprocessor.data.parser.odt
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.recovery.GracefulDegradationStrategy
import com.tosin.docprocessor.data.parser.recovery.RecoveryStrategy
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class OdtXmlParser(
    private val cacheDir: File,
    private val zipEntries: Map<String, ByteArray>,
    private val recoveryStrategy: RecoveryStrategy = GracefulDegradationStrategy()
) {

    private val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val drawNs = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"

    private lateinit var styleParser: OdtStyleParser
    private lateinit var paragraphParser: OdtParagraphParser
    private lateinit var tableParser: OdtTableParser
    private lateinit var imageParser: OdtImageParser

    fun parse(contentXmlBytes: ByteArray): List<DocumentElement> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(contentXmlBytes))
        val root = doc.documentElement

        styleParser = OdtStyleParser()
        val styles = styleParser.parseStyles(root)
        
        paragraphParser = OdtParagraphParser(styles)
        tableParser = OdtTableParser(styles)
        imageParser = OdtImageParser(cacheDir, zipEntries)

        val elements = mutableListOf<DocumentElement>()
        
        // Find office:body/office:text
        val bodyNodes = root.getElementsByTagNameNS(officeNs, "body")
        if (bodyNodes.length > 0) {
            val body = bodyNodes.item(0) as Element
            val textNodes = body.getElementsByTagNameNS(officeNs, "text")
            if (textNodes.length > 0) {
                traverse(textNodes.item(0), elements, ListContext())
            }
        }

        return elements
    }

    private data class ListContext(
        val depth: Int = 0,
        val styleName: String? = null
    )

    private fun traverse(node: Node, output: MutableList<DocumentElement>, listContext: ListContext) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                try {
                    when (element.localName) {
                        "h" -> output += paragraphParser.parseHeader(element)
                        "p" -> {
                            val listInfo = if (listContext.depth > 0) {
                                com.tosin.docprocessor.data.parser.internal.models.ListInfo(
                                    level = listContext.depth - 1,
                                    format = listContext.styleName
                                )
                            } else null
                            output += paragraphParser.parseParagraph(element, listInfo)
                        }
                        "table" -> output += tableParser.parseTable(element)
                        "list" -> {
                            val newListContext = listContext.copy(
                                depth = listContext.depth + 1,
                                styleName = element.getAttributeNS(textNs, "style-name").takeIf { it.isNotEmpty() } ?: listContext.styleName
                            )
                            traverse(child, output, newListContext)
                        }
                        "list-item" -> traverse(child, output, listContext)
                        "frame" -> imageParser.parseImage(element)?.let { output += it }
                        else -> traverse(child, output, listContext)
                    }
                } catch (e: Exception) {
                    recoveryStrategy.handleFailure("ODT Element: ${element.localName}", e) {
                        // Skip this element
                    }
                }
            }
        }
    }
}
