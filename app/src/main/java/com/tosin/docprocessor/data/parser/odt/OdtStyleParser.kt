package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import org.w3c.dom.Element
import org.w3c.dom.NodeList

class OdtStyleParser {

    private val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val styleNs = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private val foNs = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"

    /**
     * Maps style names to their formatting properties.
     */
    fun parseStyles(doc: Element): Map<String, StyleProperties> {
        val styles = mutableMapOf<String, StyleProperties>()
        
        // Find all <style:style> elements
        val styleNodes = doc.getElementsByTagNameNS(styleNs, "style")
        for (i in 0 until styleNodes.length) {
            val styleElement = styleNodes.item(i) as Element
            val name = styleElement.getAttributeNS(styleNs, "name")
            if (name.isNotEmpty()) {
                styles[name] = parseStyleProperties(styleElement)
            }
        }
        
        return styles
    }

    private fun parseStyleProperties(styleElement: Element): StyleProperties {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var color: String? = null
        var fontSize: Float? = null

        val textPropNodes = styleElement.getElementsByTagNameNS(styleNs, "text-properties")
        if (textPropNodes.length > 0) {
            val props = textPropNodes.item(0) as Element
            isBold = props.getAttributeNS(foNs, "font-weight") == "bold"
            isItalic = props.getAttributeNS(foNs, "font-style") == "italic"
            isUnderline = props.hasAttributeNS(styleNs, "text-underline-style")
            color = props.getAttributeNS(foNs, "color").takeIf { it.startsWith("#") }?.removePrefix("#")
            fontSize = props.getAttributeNS(foNs, "font-size").removeSuffix("pt").toFloatOrNull()
        }

        var alignment: String? = null
        val paraPropNodes = styleElement.getElementsByTagNameNS(styleNs, "paragraph-properties")
        if (paraPropNodes.length > 0) {
            val props = paraPropNodes.item(0) as Element
            alignment = props.getAttributeNS(foNs, "text-align")
        }

        return StyleProperties(isBold, isItalic, isUnderline, color, fontSize, alignment)
    }

    data class StyleProperties(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val color: String? = null,
        val fontSize: Float? = null,
        val alignment: String? = null
    )
}
