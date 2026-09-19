package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.parser.internal.models.OdtBorder
import com.tosin.docprocessor.data.parser.internal.models.OdtParagraphProperties
import com.tosin.docprocessor.data.parser.internal.models.OdtStyle
import com.tosin.docprocessor.data.parser.internal.models.OdtTableCellProperties
import com.tosin.docprocessor.data.parser.internal.models.OdtTableProperties
import com.tosin.docprocessor.data.parser.internal.models.OdtTextProperties
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import org.w3c.dom.Element

/**
 * Parses the ODT style system from the `styles.xml` (and content.xml automatic
 * styles) DOM. Produces a map of style-name → resolved [StyleProperties] with
 * inheritance applied along the `style:parent-style-name` chain, terminated by
 * the family's `<style:default-style>`.
 */
class OdtStyleParser {

    data class StyleProperties(
        val textProperties: OdtTextProperties? = null,
        val paragraphProperties: OdtParagraphProperties? = null,
        val tableProperties: OdtTableProperties? = null,
        val tableCellProperties: OdtTableCellProperties? = null,
        val isHeading: Boolean = false,
        val headingLevel: Int? = null
    )

    fun parseStyles(root: Element): Map<String, StyleProperties> {
        val rawStyles = mutableMapOf<String, OdtStyle>()
        val defaults = mutableMapOf<String, OdtStyle>()

        val styleNodes = root.getElementsByTagNameNS(styleNs, "style")
        for (i in 0 until styleNodes.length) {
            val element = styleNodes.item(i) as Element
            val parsed = parseRawStyle(element) ?: continue
            rawStyles[parsed.name] = parsed
        }

        val defaultNodes = root.getElementsByTagNameNS(styleNs, "default-style")
        for (i in 0 until defaultNodes.length) {
            val element = defaultNodes.item(i) as Element
            val family = element.getAttributeNS(styleNs, "family")
            if (family.isEmpty()) continue
            // default-style has no style:name attribute; build its OdtStyle directly.
            defaults[family] = OdtStyle(
                family = family,
                name = family,
                textProperties = parseTextProperties(element),
                paragraphProperties = parseParagraphProperties(element),
                tableProperties = parseTableProperties(element),
                tableCellProperties = parseTableCellProperties(element),
                isHeading = false
            )
        }

        val result = mutableMapOf<String, StyleProperties>()
        rawStyles.forEach { (name, style) ->
            val chain = buildChain(style, rawStyles, defaults)
            val textProps = mergeText(chain)
            val paraProps = mergeParagraph(chain)
            result[name] = StyleProperties(
                textProperties = textProps,
                paragraphProperties = paraProps,
                tableProperties = mergeTable(chain),
                tableCellProperties = mergeTableCell(chain),
                isHeading = chain.any { it.isHeading },
                headingLevel = chain.firstNotNullOfOrNull { it.paragraphProperties?.outlineLevel }
            )
        }
        return result
    }

    private fun parseRawStyle(element: Element): OdtStyle? {
        val name = element.getAttributeNS(styleNs, "name")
        if (name.isEmpty()) return null
        val family = element.getAttributeNS(styleNs, "family").ifEmpty {
            inferFamily(element)
        }
        val parentName = element.getAttributeNS(styleNs, "parent-style-name")
            .takeIf { it.isNotEmpty() }

        val paraProps = parseParagraphProperties(element)

        val isHeading = when {
            paraProps?.outlineLevel != null -> true
            element.getAttributeNS(styleNs, "outline-level")?.toIntOrNull() != null -> true
            name.contains("Heading", ignoreCase = true) -> true
            element.getAttributeNS(styleNs, "master-page-name")
                .contains("Heading", ignoreCase = true) -> true
            else -> false
        }

        return OdtStyle(
            family = family,
            name = name,
            parentName = parentName,
            textProperties = parseTextProperties(element),
            paragraphProperties = paraProps,
            tableProperties = parseTableProperties(element),
            tableCellProperties = parseTableCellProperties(element),
            isHeading = isHeading
        )
    }

    private fun inferFamily(element: Element): String = when {
        element.getElementsByTagNameNS(styleNs, "paragraph-properties").length > 0 -> "paragraph"
        element.getElementsByTagNameNS(styleNs, "text-properties").length > 0 -> "text"
        element.getElementsByTagNameNS(styleNs, "table-properties").length > 0 -> "table"
        element.getElementsByTagNameNS(styleNs, "table-cell-properties").length > 0 -> "table-cell"
        else -> "paragraph"
    }

    // ------------------------------------------------------------------
    // Property extraction
    // ------------------------------------------------------------------

    private fun parseTextProperties(element: Element): OdtTextProperties? {
        val nodes = element.getElementsByTagNameNS(styleNs, "text-properties")
        if (nodes.length == 0) return null
        val props = nodes.item(0) as Element

        val fontWeight = firstAttribute(props, foNs, "font-weight")
        val fontStyle = firstAttribute(props, foNs, "font-style")
        val underlineStyle = props.getAttributeNS(styleNs, "text-underline-style")
            .takeIf { it.isNotEmpty() } ?: "none"

        return OdtTextProperties(
            isBold = fontWeight.equals("bold", ignoreCase = true),
            isItalic = fontStyle.equals("italic", ignoreCase = true),
            isUnderline = underlineStyle != "none",
            color = parseHexColor(firstAttribute(props, foNs, "color")),
            fontSize = parseFontSize(firstAttribute(props, foNs, "font-size")),
            fontFamily = props.getAttributeNS(foNs, "font-family").takeIf { it.isNotEmpty() },
            fontStyle = fontStyle?.takeIf { it != "italic" && it != "normal" },
            language = props.getAttributeNS(textNs, "language").takeIf { it.isNotEmpty() },
            country = props.getAttributeNS(textNs, "country").takeIf { it.isNotEmpty() }
        )
    }

    private fun parseParagraphProperties(element: Element): OdtParagraphProperties? {
        val nodes = element.getElementsByTagNameNS(styleNs, "paragraph-properties")
        if (nodes.length == 0) return null
        val props = nodes.item(0) as Element

        val alignment = when (firstAttribute(props, foNs, "text-align")) {
            "left", "start" -> ParagraphAlignment.START
            "right", "end" -> ParagraphAlignment.END
            "center" -> ParagraphAlignment.CENTER
            "justify", "justified" -> ParagraphAlignment.JUSTIFIED
            else -> null
        }

        val left = parseTwips(firstAttribute(props, foNs, "margin-left"))
        val right = parseTwips(firstAttribute(props, foNs, "margin-right"))
        val textIndent = parseTwips(firstAttribute(props, foNs, "text-indent"))

        return OdtParagraphProperties(
            alignment = alignment,
            indentation = ParagraphIndentation(
                left = left,
                right = right,
                firstLine = textIndent?.takeIf { it >= 0 },
                hanging = textIndent?.takeIf { it < 0 }?.let { -it }
            ),
            spacing = ParagraphSpacing(
                before = parseTwips(firstAttribute(props, foNs, "space-before")),
                after = parseTwips(firstAttribute(props, foNs, "space-after")),
                line = parseTwips(firstAttribute(props, foNs, "line-spacing"))
            ),
            outlineLevel = props.getAttributeNS(styleNs, "outline-level").toIntOrNull(),
            pageBreakBefore = firstAttribute(props, styleNs, "page-break-before") == "always",
            keepTogether = firstAttribute(props, styleNs, "keep-with-next") == "always",
            border = parseBorder(props)
        )
    }

    private fun parseTableProperties(element: Element): OdtTableProperties? {
        val nodes = element.getElementsByTagNameNS(styleNs, "table-properties")
        if (nodes.length == 0) return null
        val props = nodes.item(0) as Element
        return OdtTableProperties(
            tableBorder = parseBorder(props),
            backgroundColor = parseHexColor(firstAttribute(props, foNs, "background-color")),
            width = props.getAttributeNS(foNs, "width").takeIf { it.isNotEmpty() },
            tableLayout = props.getAttributeNS(styleNs, "table-layout").takeIf { it.isNotEmpty() }
        )
    }

    private fun parseTableCellProperties(element: Element): OdtTableCellProperties? {
        val nodes = element.getElementsByTagNameNS(styleNs, "table-cell-properties")
        if (nodes.length == 0) return null
        val props = nodes.item(0) as Element
        return OdtTableCellProperties(
            backgroundColor = parseHexColor(firstAttribute(props, foNs, "background-color")),
            border = parseBorder(props),
            verticalAlignment = firstAttribute(props, foNs, "vertical-align"),
            marginLeft = parseTwips(firstAttribute(props, foNs, "margin-left")),
            marginRight = parseTwips(firstAttribute(props, foNs, "margin-right")),
            marginTop = parseTwips(firstAttribute(props, foNs, "margin-top")),
            marginBottom = parseTwips(firstAttribute(props, foNs, "margin-bottom"))
        )
    }

    // ------------------------------------------------------------------
    // Inheritance
    // ------------------------------------------------------------------

    /**
     * Builds the chain from [style] up through parents to the family default.
     * The chain is ordered default → … → parent → child so merges apply
     * inheritance first, from the root of the chain.
     */
    private fun buildChain(
        style: OdtStyle,
        allStyles: Map<String, OdtStyle>,
        defaults: Map<String, OdtStyle>
    ): List<OdtStyle> {
        val seen = mutableSetOf<String>()
        val chain = mutableListOf<OdtStyle>()
        var current: OdtStyle? = style
        while (current != null && seen.add(current.name)) {
            chain += current
            current = current.parentName?.let { allStyles[it] }
        }
        val defaultStyle = defaults[style.family]
        if (defaultStyle != null && seen.add(defaultStyle.name)) {
            chain += defaultStyle
        }
        return chain.reversed()
    }

    private fun mergeText(chain: List<OdtStyle>): OdtTextProperties? {
        var result: OdtTextProperties? = null
        chain.forEach { style ->
            val props = style.textProperties ?: return@forEach
            result = if (result == null) props else merge(result, props)
        }
        return result
    }

    private fun mergeParagraph(chain: List<OdtStyle>): OdtParagraphProperties? {
        var result: OdtParagraphProperties? = null
        chain.forEach { style ->
            val props = style.paragraphProperties ?: return@forEach
            result = if (result == null) props else merge(result, props)
        }
        return result
    }

    private fun mergeTable(chain: List<OdtStyle>): OdtTableProperties? {
        var result: OdtTableProperties? = null
        chain.forEach { style ->
            val props = style.tableProperties ?: return@forEach
            result = if (result == null) props else merge(result, props)
        }
        return result
    }

    private fun mergeTableCell(chain: List<OdtStyle>): OdtTableCellProperties? {
        var result: OdtTableCellProperties? = null
        chain.forEach { style ->
            val props = style.tableCellProperties ?: return@forEach
            result = if (result == null) props else merge(result, props)
        }
        return result
    }

    private fun merge(parent: OdtTextProperties, child: OdtTextProperties): OdtTextProperties =
        OdtTextProperties(
            isBold = child.isBold || parent.isBold,
            isItalic = child.isItalic || parent.isItalic,
            isUnderline = child.isUnderline || parent.isUnderline,
            color = child.color ?: parent.color,
            fontSize = child.fontSize ?: parent.fontSize,
            fontFamily = child.fontFamily ?: parent.fontFamily,
            fontStyle = child.fontStyle ?: parent.fontStyle,
            language = child.language ?: parent.language,
            country = child.country ?: parent.country
        )

    private fun merge(parent: OdtParagraphProperties, child: OdtParagraphProperties): OdtParagraphProperties =
        OdtParagraphProperties(
            alignment = child.alignment ?: parent.alignment,
            indentation = child.indentation ?: parent.indentation,
            spacing = child.spacing ?: parent.spacing,
            outlineLevel = child.outlineLevel ?: parent.outlineLevel,
            pageBreakBefore = child.pageBreakBefore || parent.pageBreakBefore,
            keepTogether = child.keepTogether || parent.keepTogether,
            border = child.border ?: parent.border
        )

    private fun merge(parent: OdtTableProperties, child: OdtTableProperties): OdtTableProperties =
        OdtTableProperties(
            tableBorder = child.tableBorder ?: parent.tableBorder,
            backgroundColor = child.backgroundColor ?: parent.backgroundColor,
            width = child.width ?: parent.width,
            tableLayout = child.tableLayout ?: parent.tableLayout
        )

    private fun merge(parent: OdtTableCellProperties, child: OdtTableCellProperties): OdtTableCellProperties =
        OdtTableCellProperties(
            backgroundColor = child.backgroundColor ?: parent.backgroundColor,
            border = child.border ?: parent.border,
            verticalAlignment = child.verticalAlignment ?: parent.verticalAlignment,
            marginLeft = child.marginLeft ?: parent.marginLeft,
            marginRight = child.marginRight ?: parent.marginRight,
            marginTop = child.marginTop ?: parent.marginTop,
            marginBottom = child.marginBottom ?: parent.marginBottom
        )

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Reads an attribute with the legacy FO namespace as a fallback. */
    private fun firstAttribute(element: Element, ns: String, name: String): String? {
        element.getAttributeNS(ns, name).takeIf { it.isNotEmpty() }?.let { return it }
        element.getAttributeNS(foAltNs, name).takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun parseHexColor(value: String?): String? {
        if (value == null) return null
        val withoutHash = value.uppercase().removePrefix("#")
        return when (withoutHash.length) {
            3 -> withoutHash.map { "$it$it" }.joinToString("")
            6, 8 -> withoutHash.substring(0, 6)
            else -> null
        }
    }

    private fun parseFontSize(value: String?): Float? {
        if (value == null) return null
        return value.removeSuffix("pt").trim().toFloatOrNull()
    }

    /**
     * Converts a length attribute to twips (1/1440 inch). Supports cm, mm,
     * in, pt; falls back to a plain number treated as twips (returns null for
     * percentage or auto values).
     */
    private fun parseTwips(value: String?): Int? {
        if (value.isNullOrEmpty() || value.endsWith("%")) return null
        val trimmed = value.trim()
        val magnitude = trimmed
            .removeSuffix("pt").removeSuffix("cm").removeSuffix("mm").removeSuffix("in")
            .trim().toDoubleOrNull() ?: return null
        return when {
            trimmed.endsWith("pt") -> (magnitude * 20).toInt()
            trimmed.endsWith("cm") -> (magnitude * 566.929).toInt()
            trimmed.endsWith("mm") -> (magnitude * 56.6929).toInt()
            trimmed.endsWith("in") -> (magnitude * 1440).toInt()
            else -> magnitude.toInt()
        }
    }

    private fun parseBorder(props: Element): OdtBorder? {
        val style = props.getAttributeNS(styleNs, "border")
        if (style.isEmpty()) return null
        val parts = style.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val color = parts.lastOrNull()?.let { parseHexColor(it) }
        val width = parts.firstOrNull()?.let { parseTwips(it) }
        return OdtBorder(
            width = width,
            color = color,
            style = parts.getOrNull(1) ?: "solid",
            borderSide = null
        )
    }

    companion object {
        const val styleNs = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
        const val textNs = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
        const val foNs = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
        const val foAltNs = "urn:oasis:names:tc:opendocument:xmlns:fo:1.0"
        const val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    }
}