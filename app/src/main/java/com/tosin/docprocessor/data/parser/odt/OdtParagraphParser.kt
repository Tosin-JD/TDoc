package com.tosin.docprocessor.data.parser.odt

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import com.tosin.docprocessor.data.parser.internal.models.OdtParagraphProperties
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.data.parser.internal.models.ParagraphIndentation
import com.tosin.docprocessor.data.parser.internal.models.ParagraphSpacing
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses ODT paragraphs into [DocumentElement.Paragraph]s with full paragraph
 * properties (alignment, indentation, spacing, heading), spans with text-style
 * formatting plus inline overrides, hyperlinks, and list info.
 */
class OdtParagraphParser(
    private val styles: Map<String, OdtStyleParser.StyleProperties>
) {

    private val textNs = OdtStyleParser.textNs
    private val foNs = OdtStyleParser.foNs
    private val foAltNs = OdtStyleParser.foAltNs
    private val styleNs = OdtStyleParser.styleNs
    private val tableNs = OdtStyleParser.tableNs
    private val xlinkNs = "http://www.w3.org/1999/xlink"

    fun parseParagraph(element: Element): DocumentElement.Paragraph {
        val styleName = element.getAttributeNS(textNs, "style-name").ifEmpty { null }
        val styleProps = styleName?.let { styles[it] }

        // Paragraph properties: resolved style first, then own inline properties override.
        val ownProps = parseOwnParagraphProperties(element)
        val effective = mergeParagraphProperties(styleProps?.paragraphProperties, ownProps)

        val paragraphStyle = ParagraphStyle(
            styleId = styleName,
            styleName = styleName,
            alignment = effective?.alignment ?: ParagraphAlignment.START,
            indentation = effective?.indentation ?: ParagraphIndentation(),
            spacing = effective?.spacing ?: ParagraphSpacing(),
            outlineLevel = effective?.outlineLevel ?: styleProps?.headingLevel,
            isHeading = styleProps?.isHeading == true ||
                (element.localName == "h") ||
                styleName?.contains("Heading", ignoreCase = true) == true,
            headingLevel = effective?.outlineLevel ?: styleProps?.headingLevel
        )

        val spans = mutableListOf<TextSpan>()
        val hyperlink = collectSpans(element, spans, inheritedStyle = styleProps)
        val listInfo = parseListItem(element)

        return DocumentElement.Paragraph(
            spans = spans,
            listLabel = listInfo?.let(::formatListLabel),
            style = paragraphStyle,
            hyperlink = hyperlink,
            listInfo = listInfo
        )
    }

    fun parseHeader(element: Element): DocumentElement.SectionHeader {
        val text = element.textContent.orEmpty().trim()
        val level = element.getAttributeNS(textNs, "outline-level").toIntOrNull() ?: 1
        return DocumentElement.SectionHeader(text, level)
    }

    // ------------------------------------------------------------------
    // Paragraph properties
    // ------------------------------------------------------------------

    /**
     * Reads paragraph-level properties from the paragraph element itself:
     * direct FO attributes on the element, plus a `<text:pPr>` child if present
     * (some writers emit overrides there).
     */
    private fun parseOwnParagraphProperties(element: Element): OdtStyleParser.StyleProperties? {
        val pPr = element.getElementsByTagNameNS(textNs, "pPr").item(0) as? Element ?: element
        return OdtStyleParser.StyleProperties(
            paragraphProperties = OdtParagraphProperties(
                alignment = parseAlignment(attribute(pPr, "text-align")),
                indentation = ParagraphIndentation(
                    left = parseTwips(attribute(pPr, "margin-left")),
                    right = parseTwips(attribute(pPr, "margin-right")),
                    firstLine = parseTwips(attribute(pPr, "first-line-indent")),
                    hanging = parseTwips(attribute(pPr, "text-indent"))?.takeIf { it < 0 }?.let { -it }
                ),
                spacing = ParagraphSpacing(
                    before = parseTwips(attribute(pPr, "space-before")),
                    after = parseTwips(attribute(pPr, "space-after")),
                    line = parseTwips(attribute(pPr, "line-spacing"))
                ),
                outlineLevel = element.getAttributeNS(styleNs, "outline-level").toIntOrNull()
            )
        )
    }

    private fun mergeParagraphProperties(
        base: OdtParagraphProperties?,
        own: OdtStyleParser.StyleProperties?
    ): OdtParagraphProperties? {
        val ownProps = own?.paragraphProperties ?: return base
        if (base == null) return ownProps
        return OdtParagraphProperties(
            alignment = ownProps.alignment ?: base.alignment,
            indentation = merge(base.indentation, ownProps.indentation),
            spacing = merge(base.spacing, ownProps.spacing),
            outlineLevel = ownProps.outlineLevel ?: base.outlineLevel,
            pageBreakBefore = ownProps.pageBreakBefore || base.pageBreakBefore,
            keepTogether = ownProps.keepTogether || base.keepTogether,
            border = ownProps.border ?: base.border
        )
    }

    private fun merge(base: ParagraphIndentation?, own: ParagraphIndentation?): ParagraphIndentation? =
        when {
            base == null -> own
            own == null -> base
            else -> ParagraphIndentation(
                left = own.left ?: base.left,
                right = own.right ?: base.right,
                firstLine = own.firstLine ?: base.firstLine,
                hanging = own.hanging ?: base.hanging
            )
        }

    private fun merge(base: ParagraphSpacing?, own: ParagraphSpacing?): ParagraphSpacing? =
        when {
            base == null -> own
            own == null -> base
            else -> ParagraphSpacing(
                before = own.before ?: base.before,
                after = own.after ?: base.after,
                line = own.line ?: base.line
            )
        }

    // ------------------------------------------------------------------
    // Spans
    // ------------------------------------------------------------------

    /**
     * Recursively collects span text with formatting. Returns a [HyperlinkInfo]
     * if the paragraph's single hyperlink was found (per MVP model: one
     * hyperlink per paragraph).
     */
    private fun collectSpans(
        node: Node,
        output: MutableList<TextSpan>,
        inheritedStyle: OdtStyleParser.StyleProperties?,
        hyperlink: MutableList<HyperlinkInfo> = mutableListOf()
    ): HyperlinkInfo? {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrEmpty()) {
                        output += applyInherited(
                            TextSpan(text = text, color = "000000"),
                            inheritedStyle
                        )
                    }
                }
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    when (element.localName) {
                        "span" -> {
                            val styleName = element.getAttributeNS(textNs, "style-name").ifEmpty { null }
                            val spanStyle = styleName?.let { styles[it] }
                            val rPr = element.getElementsByTagNameNS(textNs, "rPr").item(0) as? Element
                            val base = TextSpan(
                                text = element.textContent.orEmpty(),
                                isBold = spanStyle?.textProperties?.isBold == true ||
                                    rPr?.getAttributeNS(foNs, "font-weight") == "bold",
                                isItalic = spanStyle?.textProperties?.isItalic == true ||
                                    rPr?.getAttributeNS(foNs, "font-style") == "italic",
                                isUnderline = spanStyle?.textProperties?.isUnderline ?: false,
                                color = spanStyle?.textProperties?.color
                                    ?: rPr?.getAttributeNS(foNs, "color")?.let(::hex)
                                    ?: "000000",
                                fontSize = spanStyle?.textProperties?.fontSize?.toInt()
                                    ?: rPr?.getAttributeNS(foNs, "font-size")?.removeSuffix("pt")
                                        ?.toFloatOrNull()?.toInt(),
                                fontFamily = spanStyle?.textProperties?.fontFamily
                                    ?: rPr?.getAttributeNS(foNs, "font-family"),
                                language = spanStyle?.textProperties?.language
                                    ?: rPr?.getAttributeNS(textNs, "language")
                            )
                            output += applyInherited(base, inheritedStyle)
                        }
                        "s" -> {
                            val count = element.getAttributeNS(textNs, "c").toIntOrNull() ?: 1
                            output += TextSpan(text = " ".repeat(count.coerceAtLeast(1)))
                        }
                        "tab" -> output += TextSpan(text = "\t")
                        "line-break" -> output += TextSpan(text = "\n")
                        "a" -> {
                            val href = element.getAttributeNS(xlinkNs, "href")
                            if (href.isNotBlank()) {
                                hyperlink += HyperlinkInfo(
                                    address = href.takeIf { !it.startsWith("#") },
                                    anchor = href.takeIf { it.startsWith("#") }?.removePrefix("#"),
                                    tooltip = element.getAttributeNS(officeNs, "title").ifEmpty { null }
                                )
                            }
                            output += TextSpan(
                                text = element.textContent.orEmpty(),
                                isUnderline = true,
                                color = "0000EE"
                            )
                        }
                        else -> collectSpans(element, output, inheritedStyle, hyperlink)
                    }
                }
            }
        }
        return hyperlink.firstOrNull()
    }

    private fun applyInherited(
        span: TextSpan,
        style: OdtStyleParser.StyleProperties?
    ): TextSpan {
        val props = style?.textProperties ?: return span
        return span.copy(
            isBold = span.isBold || props.isBold,
            isItalic = span.isItalic || props.isItalic,
            isUnderline = span.isUnderline || props.isUnderline,
            fontSize = span.fontSize ?: props.fontSize?.toInt()
        )
    }

    // ------------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------------

    private fun parseListItem(element: Element): ListInfo? {
        var parent = element.parentNode
        var level = 0
        var listItem: Element? = null
        while (parent != null) {
            if (parent is Element) {
                when (parent.localName) {
                    "list" -> level++
                    "list-item" -> {
                        listItem = parent
                        break
                    }
                }
            }
            parent = parent.parentNode
        }
        val item = listItem ?: return null
        val numberNode = item.getElementsByTagNameNS(textNs, "number").item(0) as? Element
        val label = numberNode?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        return ListInfo(
            level = (level - 1).coerceAtLeast(0),
            format = detectFormat(item),
            levelText = label,
            startOverride = item.getAttributeNS(textNs, "start-value").toIntOrNull(),
            bulletFont = null
        )
    }

    private fun detectFormat(item: Element): String? {
        val list = item.parentNode as? Element ?: return null
        // Look up the list style for a num-format; falls back to null (label used).
        val listStyleName = list.getAttributeNS(textNs, "style-name").ifEmpty { return null }
        val listStyle = styles[listStyleName] ?: return null
        val outline = listStyle.paragraphProperties?.outlineLevel ?: return null
        return null
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun attribute(element: Element, name: String): String? {
        element.getAttributeNS(foNs, name).takeIf { it.isNotEmpty() }?.let { return it }
        element.getAttributeNS(foAltNs, name).takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun parseAlignment(value: String?): ParagraphAlignment? = when (value) {
        "left", "start" -> ParagraphAlignment.START
        "right", "end" -> ParagraphAlignment.END
        "center" -> ParagraphAlignment.CENTER
        "justify", "justified" -> ParagraphAlignment.JUSTIFIED
        else -> null
    }

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

    private fun hex(value: String): String {
        val clean = value.uppercase().removePrefix("#")
        return when (clean.length) {
            3 -> clean.map { "$it$it" }.joinToString("")
            6, 8 -> clean.substring(0, 6)
            else -> "000000"
        }
    }

    private fun formatListLabel(info: ListInfo): String? {
        if (info.levelText?.isNotBlank() == true) return info.levelText
        return when (info.format) {
            "bullet" -> "\u2022"
            "decimal" -> "${info.startOverride ?: info.level + 1}."
            "lower-latin" -> ('a' + info.level).toString()
            "upper-latin" -> ('A' + info.level).toString()
            "lower-roman" -> toRoman(info.level + 1).lowercase()
            "upper-roman" -> toRoman(info.level + 1).uppercase()
            else -> "\u2022"
        }
    }

    private fun toRoman(value: Int): String {
        if (value <= 0 || value >= 4000) return value.toString()
        val numerals = arrayOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var remaining = value
        val builder = StringBuilder()
        for ((number, numeral) in numerals) {
            while (remaining >= number) {
                builder.append(numeral)
                remaining -= number
            }
        }
        return builder.toString()
    }

    companion object {
        private const val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    }
}