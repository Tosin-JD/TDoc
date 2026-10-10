package com.tosin.docprocessor.data.parser.common

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Namespace URIs for OOXML/WordprocessingML and OpenDocument, plus
 * namespace-aware DOM accessors.
 *
 * Dispatch must always verify the namespace URI, never the source prefix.
 */
object Ns {
    // OOXML / WordprocessingML
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val WPS = "http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
    const val MC = "http://schemas.openxmlformats.org/markup-compatibility/2006"
    const val VML = "urn:schemas-microsoft-com:vml"
    const val O = "urn:schemas-microsoft-com:office:office"
    const val M = "http://schemas.openxmlformats.org/officeDocument/2006/math"
    const val CTS = "http://schemas.openxmlformats.org/package/2006/content-types"
    const val PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val DC = "http://purl.org/dc/elements/1.1/"
    const val DCTERMS = "http://purl.org/dc/terms/"
    const val CP = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
    const val EP = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
    const val CUST = "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
    const val VT = "http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"

    // OpenDocument
    const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    const val XLINK = "http://www.w3.org/1999/xlink"
    const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
    const val META = "urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
    const val CONFIG = "urn:oasis:names:tc:opendocument:xmlns:config:1.0"
    const val MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
    const val NUMBER = "urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0"
    const val ANIM = "urn:oasis:names:tc:opendocument:xmlns:animation:1.0"
    const val OFFICE_ANNOTATION = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
}

fun Node.namespace(): String? = namespaceURI

fun Element.matchesNs(namespaceUri: String, local: String): Boolean =
    namespaceURI == namespaceUri && (localName ?: nodeName.substringAfter(':', nodeName)) == local

fun Element.elementsNs(namespaceUri: String, local: String): List<Element> {
    val output = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val child = nodes.item(i)
        if (child is Element && child.matchesNs(namespaceUri, local)) output += child
    }
    return output
}

fun Element.firstNs(namespaceUri: String, local: String): Element? =
    elementsNs(namespaceUri, local).firstOrNull()

fun Element.descendantsNs(namespaceUri: String, local: String): List<Element> {
    val output = mutableListOf<Element>()
    fun walk(node: Node) {
        val nodes = node.childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element) {
                if (child.matchesNs(namespaceUri, local)) output += child
                walk(child)
            }
        }
    }
    walk(this)
    return output
}

fun Element.attrNs(namespaceUri: String, local: String): String? =
    getAttributeNS(namespaceUri, local).takeIf { it.isNotEmpty() }

/** WordprocessingML convenience accessors. */
fun Element.w(local: String): List<Element> = elementsNs(Ns.W, local)
fun Element.wFirst(local: String): Element? = firstNs(Ns.W, local)
fun Element.wAttr(local: String): String? = attrNs(Ns.W, local)
fun Element.rAttr(local: String): String? = attrNs(Ns.R, local)
fun Element.rId(): String? = rAttr("id") ?: attrNs(Ns.R, "embed")

/** OpenDocument convenience accessors. */
fun Element.odfText(local: String): List<Element> = elementsNs(Ns.TEXT, local)
fun Element.odfTextFirst(local: String): Element? = firstNs(Ns.TEXT, local)
fun Element.odfTextAttr(local: String): String? = attrNs(Ns.TEXT, local)
