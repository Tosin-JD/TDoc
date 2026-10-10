package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.model.OpaqueNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Serializes an unknown DOM subtree with namespace declarations and
 * attribute/child order intact. Falls back to a safe textual form only when
 * the platform transformer is unavailable.
 */
object OpaqueFragmentCapture {

    fun capture(
        element: Element,
        anchor: SourceAnchor? = null,
        reason: String? = null
    ): OpaqueNode {
        val local = element.localName ?: element.nodeName.substringAfter(':', element.nodeName)
        return OpaqueNode(
            namespaceUri = element.namespaceURI,
            localName = local,
            serializedXml = serialize(element),
            anchor = anchor,
            reason = reason
        )
    }

    private fun serialize(element: Element): String = runCatching {
        val transformer = TransformerFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(element), StreamResult(writer))
        writer.toString()
    }.getOrElse {
        "<${element.nodeName}/>"
    }
}
