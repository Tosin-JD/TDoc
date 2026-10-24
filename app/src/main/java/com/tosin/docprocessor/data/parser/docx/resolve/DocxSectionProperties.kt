package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.PageMargins
import com.tosin.docprocessor.domain.document.model.style.PageOrientation
import com.tosin.docprocessor.domain.document.model.style.PageStyle
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import org.w3c.dom.Element

/** Pure `w:sectPr` parser shared by the document and block mappers. */
object DocxSectionProperties {

    fun parse(sectPr: Element, index: Int, evenAndOddHeaders: Boolean = false): SectionStyle {
        val pgSz = sectPr.wFirst("pgSz")
        val pgMar = sectPr.wFirst("pgMar")
        val cols = sectPr.wFirst("cols")
        val orientation = when (pgSz?.wAttr("orient")?.lowercase()) {
            "landscape" -> PageOrientation.LANDSCAPE
            "portrait" -> PageOrientation.PORTRAIT
            else -> null
        }
        val equalWidth = cols?.wAttr("equalWidth")?.let { it == "1" || it == "true" } ?: true
        val columnWidths = if (equalWidth) emptyList() else {
            cols?.w("col")?.map { Units.twipsToPoints(it.wAttr("w")?.toIntOrNull()) }.orEmpty()
        }
        return SectionStyle(
            index = index,
            type = sectPr.wFirst("type")?.wAttr("val"),
            page = PageStyle(
                widthPoints = Units.twipsToPoints(pgSz?.wAttr("w")?.toIntOrNull()),
                heightPoints = Units.twipsToPoints(pgSz?.wAttr("h")?.toIntOrNull()),
                orientation = orientation,
                margins = PageMargins(
                    topPoints = Units.twipsToPoints(pgMar?.wAttr("top")?.toIntOrNull()),
                    rightPoints = Units.twipsToPoints(pgMar?.wAttr("right")?.toIntOrNull()),
                    bottomPoints = Units.twipsToPoints(pgMar?.wAttr("bottom")?.toIntOrNull()),
                    leftPoints = Units.twipsToPoints(pgMar?.wAttr("left")?.toIntOrNull()),
                    headerPoints = Units.twipsToPoints(pgMar?.wAttr("header")?.toIntOrNull()),
                    footerPoints = Units.twipsToPoints(pgMar?.wAttr("footer")?.toIntOrNull()),
                    gutterPoints = Units.twipsToPoints(pgMar?.wAttr("gutter")?.toIntOrNull())
                ),
                columns = cols?.wAttr("num")?.toIntOrNull(),
                columnSpacingPoints = Units.twipsToPoints(cols?.wAttr("space")?.toIntOrNull()),
                columnWidths = columnWidths,
                verticalAlign = sectPr.wFirst("vAlign")?.wAttr("val"),
                pageBorders = DocxBorderProperties.parseCellBorders(sectPr.wFirst("pgBorders"))
                    .takeIf { it.top != null || it.right != null || it.bottom != null || it.left != null },
                lineNumberingStep = sectPr.wFirst("lnNumType")?.wAttr("countBy")?.toIntOrNull()
                    ?.takeIf { it > 0 },
                notePlacement = sectPr.wFirst("footnotePr")?.wAttr("pos")
                    ?: sectPr.wFirst("endnotePr")?.wAttr("pos")
            ),
            pageNumberStart = sectPr.wFirst("pgNumType")?.wAttr("start")?.toIntOrNull(),
            hasDifferentFirstPage = sectPr.wFirst("titlePg") != null,
            hasDifferentEvenOdd = evenAndOddHeaders
        )
    }
}
