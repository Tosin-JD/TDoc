package com.tosin.docprocessor.data.parser.odt.resolve

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.ListKind
import com.tosin.docprocessor.domain.document.model.style.ListLevel
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.domain.document.model.style.PageMargins
import com.tosin.docprocessor.domain.document.model.style.PageOrientation
import com.tosin.docprocessor.domain.document.model.style.PageStyle
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TabStop
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.domain.document.model.style.UnderlineKind
import com.tosin.docprocessor.domain.document.model.style.VerticalAlign
import com.tosin.docprocessor.domain.document.model.style.VerticalCellAlign
import org.w3c.dom.Element

/** Parses ODF `style:*` property containers into domain styles. */
object OdfTextProperties {
    fun parse(styleElement: Element): TextStyle {
        val props = styleElement.firstNs(Ns.STYLE, "text-properties") ?: return TextStyle()
        val fontFamily = props.attrNs(Ns.FO, "font-family")?.substringBefore(',')?.trim()
            ?: props.attrNs(Ns.STYLE, "font-family-asian")?.substringBefore(',')?.trim()
        return TextStyle(
            fontAscii = fontFamily,
            fontHAnsi = fontFamily,
            fontSizePoints = Units.parseOdfLength(props.attrNs(Ns.FO, "font-size")),
            bold = when (props.attrNs(Ns.FO, "font-weight")) {
                "bold", "700", "800", "900" -> true
                "normal", "400" -> false
                else -> null
            },
            italic = when (props.attrNs(Ns.FO, "font-style")) {
                "italic", "oblique" -> true
                "normal" -> false
                else -> null
            },
            underline = underline(props.attrNs(Ns.STYLE, "text-underline-style")),
            strike = when (props.attrNs(Ns.STYLE, "text-line-through-style")) {
                null, "none" -> null
                else -> true
            },
            verticalAlign = verticalAlign(props.attrNs(Ns.STYLE, "text-position")),
            color = props.attrNs(Ns.FO, "color"),
            highlight = props.attrNs(Ns.STYLE, "text-background-color"),
            shading = props.attrNs(Ns.FO, "background-color")?.let { Shading(fill = it) },
            caps = when (props.attrNs(Ns.FO, "text-transform")) {
                "uppercase" -> true
                "lowercase", "capitalize" -> false
                else -> null
            },
            letterSpacingPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "letter-spacing")),
            language = props.attrNs(Ns.FO, "language"),
            direction = props.attrNs(Ns.FO, "direction")
                ?: props.attrNs(Ns.STYLE, "writing-mode")
        )
    }

    private fun underline(value: String?): UnderlineKind? = when (value) {
        null, "none" -> null
        "double" -> UnderlineKind.DOUBLE
        "dotted" -> UnderlineKind.DOTTED
        "dash", "long-dash", "dot-dash", "dot-dot-dash" -> UnderlineKind.DASHED
        "wave" -> UnderlineKind.WAVE
        else -> UnderlineKind.SINGLE
    }

    private fun verticalAlign(position: String?): VerticalAlign? {
        val probe = position?.substringBefore(' ')?.lowercase() ?: return null
        return when {
            probe.startsWith("super") || (probe.toIntOrNull()?.let { it > 0 } == true) -> VerticalAlign.SUPERSCRIPT
            probe.startsWith("sub") || (probe.toIntOrNull()?.let { it < 0 } == true) -> VerticalAlign.SUBSCRIPT
            else -> VerticalAlign.BASELINE
        }
    }
}

object OdfParagraphProperties {
    fun parse(styleElement: Element): ParagraphStyle {
        val props = styleElement.firstNs(Ns.STYLE, "paragraph-properties")
            ?: return ParagraphStyle(
                styleName = styleElement.attrNs(Ns.STYLE, "name"),
                basedOn = styleElement.attrNs(Ns.STYLE, "parent-style-name"),
                next = styleElement.attrNs(Ns.STYLE, "next-style-name"),
                masterPageName = styleElement.attrNs(Ns.STYLE, "master-page-name")
            )
        val indent = Units.parseOdfLength(props.attrNs(Ns.FO, "text-indent"))
        val tabStops = props.firstNs(Ns.STYLE, "tab-stops")?.elementsNs(Ns.STYLE, "tab-stop")
            ?: props.elementsNs(Ns.STYLE, "tab-stop")
        return ParagraphStyle(
            styleName = styleElement.attrNs(Ns.STYLE, "name"),
            basedOn = styleElement.attrNs(Ns.STYLE, "parent-style-name"),
            next = styleElement.attrNs(Ns.STYLE, "next-style-name"),
            alignment = alignment(props.attrNs(Ns.FO, "text-align")),
            indentationStartPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-left")),
            indentationEndPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-right")),
            firstLineIndentPoints = indent?.takeIf { it >= 0 },
            hangingIndentPoints = indent?.takeIf { it < 0 }?.let { -it },
            spaceBeforePoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-top")),
            spaceAfterPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-bottom")),
            lineSpacing = lineSpacing(props.attrNs(Ns.FO, "line-height")),
            lineSpacingRule = lineSpacingRule(props.attrNs(Ns.FO, "line-height")),
            keepNext = boolean(props.attrNs(Ns.FO, "keep-with-next")),
            keepLines = boolean(props.attrNs(Ns.FO, "keep-together")),
            pageBreakBefore = props.attrNs(Ns.FO, "break-before")?.contains("page") == true,
            tabs = tabStops.mapNotNull { tab ->
                val position = Units.parseOdfLength(tab.attrNs(Ns.STYLE, "position")) ?: return@mapNotNull null
                TabStop(
                    positionPoints = position,
                    alignment = tab.attrNs(Ns.STYLE, "type"),
                    leader = tab.attrNs(Ns.STYLE, "leader-style")
                )
            },
            masterPageName = styleElement.attrNs(Ns.STYLE, "master-page-name")
                ?: props.attrNs(Ns.STYLE, "master-page-name"),
            shading = props.attrNs(Ns.FO, "background-color")?.let { Shading(fill = it) },
            borders = props.attrNs(Ns.FO, "border")?.let { listOf(parseBorder(it)) }.orEmpty(),
            defaultRunStyle = OdfTextProperties.parse(styleElement).let { runStyle ->
                val direction = props.attrNs(Ns.FO, "direction")
                    ?: props.attrNs(Ns.STYLE, "writing-mode")
                if (direction == null) runStyle else runStyle.copy(direction = direction)
            }.takeIf { it != TextStyle() }
        )
    }

    fun alignment(value: String?): TextAlignment? = when (value?.lowercase()) {
        "start", "left" -> TextAlignment.START
        "end", "right" -> TextAlignment.END
        "center" -> TextAlignment.CENTER
        "justify" -> TextAlignment.JUSTIFIED
        else -> null
    }

    private fun lineSpacing(value: String?): Double? {
        val raw = value?.trim() ?: return null
        return if (raw.endsWith("%")) raw.dropLast(1).toDoubleOrNull()?.div(100.0)
        else Units.parseOdfLength(raw)
    }

    private fun lineSpacingRule(value: String?): String? {
        val raw = value?.trim()?.lowercase() ?: return null
        if (raw.isEmpty() || raw == "normal") return null
        return if (raw.endsWith("%") || raw.toDoubleOrNull() != null) "auto" else "exact"
    }

    private fun boolean(value: String?): Boolean? = when (value?.lowercase()) {
        "always", "true" -> true
        "auto", "false" -> false
        else -> null
    }

    private val NO_PAINT_BORDER_STYLES = setOf("none", "hidden")

    /** ODF/CSS border line styles passed through to renderers verbatim. */
    private val BORDER_STYLE_TOKENS = setOf(
        "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset"
    )

    /** CSS border-width keywords mapped to points (thin=1px, medium=3px, thick=5px). */
    private val BORDER_WIDTH_KEYWORDS = mapOf("thin" to 0.75, "medium" to 2.25, "thick" to 3.75)

    private val RGB_FUNCTION_REGEX = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
    private val RGB_SPACES_REGEX = Regex("""(rgba?\()[^)]*\)""")

    /** Common CSS/X11 colour names mapped to `#RRGGBB` for downstream parsing. */
    private val NAMED_COLORS = mapOf(
        "black" to "#000000", "white" to "#FFFFFF", "red" to "#FF0000",
        "green" to "#008000", "blue" to "#0000FF", "yellow" to "#FFFF00",
        "cyan" to "#00FFFF", "aqua" to "#00FFFF", "magenta" to "#FF00FF",
        "fuchsia" to "#FF00FF", "gray" to "#808080", "grey" to "#808080",
        "silver" to "#C0C0C0", "maroon" to "#800000", "olive" to "#808000",
        "lime" to "#00FF00", "teal" to "#008080", "navy" to "#000080",
        "purple" to "#800080", "orange" to "#FFA500", "brown" to "#A52A2A",
        "pink" to "#FFC0CB", "gold" to "#FFD700", "beige" to "#F5F5DC",
        "ivory" to "#FFFFF0", "khaki" to "#F0E68C", "lavender" to "#E6E6FA",
        "coral" to "#FF7F50", "salmon" to "#FA8072", "tomato" to "#FF6347",
        "violet" to "#EE82EE", "indigo" to "#4B0082", "turquoise" to "#40E0D0"
    )

    /**
     * Parses an ODF border shorthand (`fo:border`, `fo:border-top`, …).
     * Tokens may appear in any order: width (`0.5pt`, `thin`/`medium`/`thick`),
     * style (`solid`, `dashed`, …) and colour (`#hex`, `rgb()`, named colour).
     * `none`/`hidden` anywhere in the value is an explicit no-paint marker.
     */
    fun parseBorder(value: String): Border {
        // `rgb()` functional notation may contain spaces; strip them first so
        // whitespace tokenisation cannot split the colour into pieces.
        val normalized = RGB_SPACES_REGEX.replace(value.trim()) { match ->
            match.value.filterNot { it.isWhitespace() }
        }
        val parts = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Border(style = "solid")

        var style: String? = null
        var width: Double? = null
        var color: String? = null
        for (token in parts) {
            val lower = token.lowercase()
            when {
                lower in NO_PAINT_BORDER_STYLES ->
                    return Border(style = "none", widthPoints = 0.0)
                lower in BORDER_STYLE_TOKENS -> style = lower
                lower in BORDER_WIDTH_KEYWORDS -> width = BORDER_WIDTH_KEYWORDS[lower]
                else -> {
                    val parsedWidth = Units.parseOdfLength(token)
                    when {
                        parsedWidth != null -> width = parsedWidth
                        else -> borderColor(token)?.let { color = it }
                    }
                }
            }
        }
        return Border(style = style ?: "solid", widthPoints = width, color = color)
    }

    /** Normalises a colour token to `#RRGGBB`; null when the token is not a colour. */
    private fun borderColor(token: String): String? {
        if (token.startsWith("#")) {
            val hex = token.removePrefix("#")
            return if (hex.length in setOf(3, 6, 8) && hex.all { it.digitToIntOrNull(16) != null }) {
                "#$hex"
            } else {
                null
            }
        }
        RGB_FUNCTION_REGEX.matchEntire(token)?.let { match ->
            val r = match.groupValues[1].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val g = match.groupValues[2].toIntOrNull()?.coerceIn(0, 255) ?: return null
            val b = match.groupValues[3].toIntOrNull()?.coerceIn(0, 255) ?: return null
            return "#%02X%02X%02X".format(r, g, b)
        }
        return NAMED_COLORS[token.lowercase()]
    }
}

object OdfTableProperties {
    fun parseTable(styleElement: Element): TableStyle {
        val props = styleElement.firstNs(Ns.STYLE, "table-properties") ?: return TableStyle()
        return TableStyle(
            styleName = styleElement.attrNs(Ns.STYLE, "name"),
            widthPoints = Units.parseOdfLength(props.attrNs(Ns.STYLE, "width") ?: props.attrNs(Ns.FO, "width")),
            alignment = when (props.attrNs(Ns.TABLE, "align")) {
                "center" -> TextAlignment.CENTER
                "right", "end" -> TextAlignment.END
                "left", "start", "margins" -> TextAlignment.START
                else -> null
            },
            shading = props.attrNs(Ns.FO, "background-color")?.let { Shading(fill = it) },
            borders = parseBorders(props),
            cellMargins = parsePadding(props)
        )
    }

    fun parseCell(styleElement: Element): CellStyle {
        val props = styleElement.firstNs(Ns.STYLE, "table-cell-properties")
        return CellStyle(
            widthPoints = Units.parseOdfLength(props?.attrNs(Ns.STYLE, "width")),
            verticalAlign = when (props?.attrNs(Ns.STYLE, "vertical-align")?.lowercase()) {
                "top" -> VerticalCellAlign.TOP
                "middle", "center" -> VerticalCellAlign.CENTER
                "bottom" -> VerticalCellAlign.BOTTOM
                else -> null
            },
            shading = props?.attrNs(Ns.FO, "background-color")?.let { Shading(fill = it) },
            borders = parseBorders(props),
            margins = parsePadding(props),
            textDirection = props?.attrNs(Ns.FO, "text-direction")
                ?: props?.attrNs(Ns.STYLE, "writing-mode")
        )
    }

    /**
     * Side-specific ODF borders: `fo:border-{top,right,bottom,left}` win per
     * side, falling back to the uniform `fo:border` for unspecified sides.
     */
    private fun parseBorders(props: Element?): CellBorders {
        if (props == null) return CellBorders()
        val uniform = props.attrNs(Ns.FO, "border")?.let { OdfParagraphProperties.parseBorder(it) }
        fun side(name: String): Border? =
            props.attrNs(Ns.FO, name)?.let { OdfParagraphProperties.parseBorder(it) } ?: uniform
        return CellBorders(
            top = side("border-top"),
            right = side("border-right"),
            bottom = side("border-bottom"),
            left = side("border-left")
        )
    }

    /** Cell padding from `fo:padding*` — never shares values with borders. */
    private fun parsePadding(props: Element?): CellBorders? {
        if (props == null) return null
        fun side(override: String?, fallback: String?): Border? =
            Units.parseOdfLength(override ?: fallback)?.let { Border(style = "padding", widthPoints = it) }

        val uniform = props.attrNs(Ns.FO, "padding")
        val margins = CellBorders(
            top = side(props.attrNs(Ns.FO, "padding-top"), uniform),
            right = side(props.attrNs(Ns.FO, "padding-right"), uniform),
            bottom = side(props.attrNs(Ns.FO, "padding-bottom"), uniform),
            left = side(props.attrNs(Ns.FO, "padding-left"), uniform)
        )
        return margins.takeIf {
            it.top != null || it.right != null || it.bottom != null || it.left != null
        }
    }

    fun parseColumnWidth(styleElement: Element?): Double? {
        val props = styleElement?.firstNs(Ns.STYLE, "table-column-properties") ?: return null
        return Units.parseOdfLength(props.attrNs(Ns.STYLE, "column-width"))
    }
}

object OdfPageProperties {
    fun parse(pageLayout: Element): PageStyle {
        val props = pageLayout.firstNs(Ns.STYLE, "page-layout-properties") ?: return PageStyle()
        val width = Units.parseOdfLength(props.attrNs(Ns.FO, "page-width"))
        val height = Units.parseOdfLength(props.attrNs(Ns.FO, "page-height"))
        val orientation = when (props.attrNs(Ns.STYLE, "print-orientation")?.lowercase()) {
            "landscape" -> PageOrientation.LANDSCAPE
            "portrait" -> PageOrientation.PORTRAIT
            else -> null
        }
        return PageStyle(
            widthPoints = width,
            heightPoints = height,
            orientation = orientation,
            margins = PageMargins(
                topPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-top")),
                rightPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-right")),
                bottomPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-bottom")),
                leftPoints = Units.parseOdfLength(props.attrNs(Ns.FO, "margin-left")),
                headerPoints = Units.parseOdfLength(props.attrNs(Ns.STYLE, "header-height")),
                footerPoints = Units.parseOdfLength(props.attrNs(Ns.STYLE, "footer-height"))
            ),
            columns = props.attrNs(Ns.STYLE, "columns")?.toIntOrNull(),
            background = props.attrNs(Ns.FO, "background-color")
        )
    }
}

object OdfListProperties {
    fun parse(styleElement: Element): ListStyle {
        val levels = linkedMapOf<Int, ListLevel>()
        var kind = ListKind.UNORDERED
        val bullet = styleElement.elementsNs(Ns.TEXT, "list-level-style-bullet")
        val numbered = styleElement.elementsNs(Ns.TEXT, "list-level-style-number")
        bullet.forEach { level ->
            val index = level.attrNs(Ns.TEXT, "level")?.toIntOrNull() ?: return@forEach
            val props = level.firstNs(Ns.TEXT, "list-level-properties")
            val char = props?.attrNs(Ns.TEXT, "bullet-char") ?: level.attrNs(Ns.TEXT, "bullet-char")
            levels[index] = ListLevel(
                level = index,
                format = "bullet",
                levelText = char,
                bulletChar = char,
                indentPoints = Units.parseOdfLength(props?.attrNs(Ns.FO, "text-indent"))
            )
        }
        numbered.forEach { level ->
            kind = ListKind.ORDERED
            val index = level.attrNs(Ns.TEXT, "level")?.toIntOrNull() ?: return@forEach
            val format = level.attrNs(Ns.TEXT, "num-format")
            val props = level.firstNs(Ns.TEXT, "list-level-properties")
            // Real ODF puts num-prefix/num-suffix on the level element itself;
            // tolerate the properties-node variant some producers emit.
            val prefix = level.attrNs(Ns.STYLE, "num-prefix")
                ?: props?.attrNs(Ns.STYLE, "num-prefix").orEmpty()
            val suffix = level.attrNs(Ns.STYLE, "num-suffix")
                ?: props?.attrNs(Ns.STYLE, "num-suffix").orEmpty()
            levels[index] = ListLevel(
                level = index,
                format = format,
                // ODF substitutes the level's own 1-based number: %1, %2, …
                levelText = "$prefix%$index$suffix",
                start = level.attrNs(Ns.TEXT, "start-value")?.toIntOrNull(),
                indentPoints = Units.parseOdfLength(props?.attrNs(Ns.FO, "text-indent"))
            )
        }
        return ListStyle(
            styleId = styleElement.attrNs(Ns.STYLE, "name"),
            styleName = styleElement.attrNs(Ns.STYLE, "name"),
            levels = levels,
            kind = kind
        )
    }
}
