package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.Shading
import com.tosin.docprocessor.domain.document.model.style.TabStop
import com.tosin.docprocessor.domain.document.model.style.TextAlignment
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import com.tosin.docprocessor.domain.document.model.style.UnderlineKind
import com.tosin.docprocessor.domain.document.model.style.VerticalAlign
import org.w3c.dom.Element

/** Parses `w:rPr` into a domain [TextStyle]. */
object DocxRunProperties {

    fun parse(rPr: Element?): TextStyle {
        if (rPr == null) return TextStyle()
        val rFonts = rPr.wFirst("rFonts")
        val color = rPr.wFirst("color")
        val shading = rPr.wFirst("shd")
        return TextStyle(
            fontAscii = rFonts?.wAttr("ascii") ?: rFonts?.wAttr("asciiTheme"),
            fontHAnsi = rFonts?.wAttr("hAnsi"),
            fontEastAsia = rFonts?.wAttr("eastAsia"),
            fontComplexScript = rFonts?.wAttr("cs"),
            fontSizePoints = Units.halfPointsToPoints(rPr.wFirst("sz")?.wAttr("val")?.toIntOrNull()),
            bold = onOff(rPr.wFirst("b")),
            italic = onOff(rPr.wFirst("i")),
            underline = underline(rPr.wFirst("u")),
            underlineColor = rPr.wFirst("u")?.wAttr("color"),
            strike = onOff(rPr.wFirst("strike")) ?: onOff(rPr.wFirst("dstrike")),
            verticalAlign = verticalAlign(rPr.wFirst("vertAlign")),
            color = color?.wAttr("val")?.takeUnless { it.equals("auto", true) },
            colorTheme = color?.wAttr("themeColor"),
            highlight = rPr.wFirst("highlight")?.wAttr("val"),
            shading = shading?.let {
                Shading(fill = it.wAttr("fill"), color = it.wAttr("color"), pattern = it.wAttr("val"))
            },
            caps = onOff(rPr.wFirst("caps")),
            smallCaps = onOff(rPr.wFirst("smallCaps")),
            letterSpacingPoints = Units.twipsToPoints(rPr.wFirst("spacing")?.wAttr("val")?.toIntOrNull()),
            kerningPoints = Units.halfPointsToPoints(rPr.wFirst("kern")?.wAttr("val")?.toIntOrNull()),
            positionPoints = Units.halfPointsToPoints(rPr.wFirst("position")?.wAttr("val")?.toIntOrNull()),
            language = rPr.wFirst("lang")?.wAttr("val"),
            rightToLeft = onOff(rPr.wFirst("rtl")),
            hidden = onOff(rPr.wFirst("vanish")) ?: onOff(rPr.wFirst("webHidden"))
        )
    }

    fun underline(element: Element?): UnderlineKind? {
        if (element == null) return null
        return when (element.wAttr("val")?.lowercase() ?: "single") {
            "none" -> UnderlineKind.NONE
            "single", "words" -> UnderlineKind.SINGLE
            "double" -> UnderlineKind.DOUBLE
            "dotted", "dotDash", "dotDotDash" -> UnderlineKind.DOTTED
            "dashed", "dash" -> UnderlineKind.DASHED
            "wave", "wavy" -> UnderlineKind.WAVE
            else -> UnderlineKind.SINGLE
        }
    }

    private fun verticalAlign(element: Element?): VerticalAlign? = when (element?.wAttr("val")?.lowercase()) {
        "superscript" -> VerticalAlign.SUPERSCRIPT
        "subscript" -> VerticalAlign.SUBSCRIPT
        null -> null
        else -> VerticalAlign.BASELINE
    }

    fun onOff(element: Element?): Boolean? {
        if (element == null) return null
        val value = element.wAttr("val") ?: element.getAttributeNS(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val"
        )
        return when (value.lowercase()) {
            "", "1", "true", "on" -> true
            "0", "false", "off" -> false
            else -> true
        }
    }
}

/** Parses `w:pPr` into a domain [ParagraphStyle]. */
object DocxParagraphProperties {

    fun parse(pPr: Element?): ParagraphStyle {
        if (pPr == null) return ParagraphStyle()
        val ind = pPr.wFirst("ind")
        val spacing = pPr.wFirst("spacing")
        val pStyle = pPr.wFirst("pStyle")?.wAttr("val")
        return ParagraphStyle(
            styleId = pStyle,
            alignment = alignment(pPr.wFirst("jc")?.wAttr("val")),
            indentationStartPoints = Units.twipsToPoints(
                ind?.wAttr("start")?.toIntOrNull() ?: ind?.wAttr("left")?.toIntOrNull()
            ),
            indentationEndPoints = Units.twipsToPoints(
                ind?.wAttr("end")?.toIntOrNull() ?: ind?.wAttr("right")?.toIntOrNull()
            ),
            firstLineIndentPoints = Units.twipsToPoints(ind?.wAttr("firstLine")?.toIntOrNull()),
            hangingIndentPoints = Units.twipsToPoints(ind?.wAttr("hanging")?.toIntOrNull()),
            spaceBeforePoints = Units.twipsToPoints(spacing?.wAttr("before")?.toIntOrNull()),
            spaceAfterPoints = Units.twipsToPoints(spacing?.wAttr("after")?.toIntOrNull()),
            lineSpacing = lineSpacing(spacing),
            lineSpacingRule = spacing?.wAttr("lineRule"),
            keepNext = DocxRunProperties.onOff(pPr.wFirst("keepNext")),
            keepLines = DocxRunProperties.onOff(pPr.wFirst("keepLines")),
            pageBreakBefore = DocxRunProperties.onOff(pPr.wFirst("pageBreakBefore")),
            widowControl = DocxRunProperties.onOff(pPr.wFirst("widowControl")),
            outlineLevel = pPr.wFirst("outlineLvl")?.wAttr("val")?.toIntOrNull(),
            tabs = parseTabs(pPr.wFirst("tabs")),
            borders = parseBorders(pPr.wFirst("pBdr")),
            shading = pPr.wFirst("shd")?.let {
                Shading(fill = it.wAttr("fill"), color = it.wAttr("color"), pattern = it.wAttr("val"))
            },
            defaultRunStyle = DocxRunProperties.parse(pPr.wFirst("rPr")).takeIf { it != TextStyle() }
        )
    }

    private fun lineSpacing(spacing: Element?): Double? {
        val line = spacing?.wAttr("line")?.toIntOrNull() ?: return null
        val rule = spacing.wAttr("lineRule")
        return if (rule == "auto") line / 240.0 else Units.twipsToPoints(line)
    }

    private fun parseTabs(tabs: Element?): List<TabStop> {
        if (tabs == null) return emptyList()
        return tabs.w("tab").mapNotNull { tab ->
            val position = tab.wAttr("pos")?.toIntOrNull() ?: return@mapNotNull null
            TabStop(
                positionPoints = Units.twipsToPoints(position) ?: 0.0,
                alignment = tab.wAttr("val"),
                leader = tab.wAttr("leader")
            )
        }
    }

    private fun parseBorders(pBdr: Element?): List<Border> {
        if (pBdr == null) return emptyList()
        val sides = pBdr.w("top") + pBdr.w("left") + pBdr.w("bottom") + pBdr.w("right")
        return sides.map { side ->
            Border(
                style = side.wAttr("val"),
                widthPoints = (side.wAttr("sz")?.toIntOrNull() ?: 0) / 8.0,
                color = side.wAttr("color")?.takeUnless { it.equals("auto", true) },
                space = Units.twipsToPoints(side.wAttr("space")?.toIntOrNull())
            )
        }
    }

    fun alignment(value: String?): TextAlignment? = when (value?.lowercase()) {
        "start", "left" -> TextAlignment.START
        "end", "right" -> TextAlignment.END
        "center" -> TextAlignment.CENTER
        "both", "justify" -> TextAlignment.JUSTIFIED
        "distribute" -> TextAlignment.DISTRIBUTED
        else -> null
    }
}
