package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TableBorderSide
import org.w3c.dom.Element

/** Shared WordprocessingML border/margin parsing used by table + style mappers. */
object DocxBorderProperties {

    /** Border styles that explicitly suppress painting on their side. */
    private val NO_PAINT_STYLES = setOf("nil", "none", "zero")

    /**
     * Parses a `w:*Borders` container (`tblBorders`, `tcBorders`).
     *
     * Tri-state preservation: an absent side stays `null` (unspecified, may
     * inherit); an explicit `w:val="nil"|"none"|"zero"` side is kept as a
     * [Border] carrying that style so renderers can distinguish "no border"
     * from "not specified" and suppress inheritance correctly.
     */
    fun parseCellBorders(parent: Element?): CellBorders {
        if (parent == null) return CellBorders()
        fun border(name: String): Border? {
            val element = parent.wFirst(name) ?: return null
            val style = element.wAttr("val")
            if (style != null && NO_PAINT_STYLES.contains(style.lowercase())) {
                return Border(style = style.lowercase(), widthPoints = 0.0)
            }
            return Border(
                style = style,
                widthPoints = (element.wAttr("sz")?.toIntOrNull() ?: 0) / 8.0,
                color = element.wAttr("color")?.takeUnless { it.equals("auto", true) },
                space = Units.twipsToPoints(element.wAttr("space")?.toIntOrNull())
            )
        }
        return CellBorders(
            top = border("top"),
            right = border("right"),
            bottom = border("bottom"),
            left = border("left")
        )
    }

    /**
     * Parses `w:insideH`/`w:insideV` from a `w:tblBorders` container into a
     * [CellBorders] where horizontal interior lines map to top/bottom and
     * vertical interior lines map to left/right.
     */
    fun parseInsideBorders(tblBorders: Element?): CellBorders? {
        if (tblBorders == null) return null
        val inside = CellBorders()
        val horizontal = parseSingleBorder(tblBorders.wFirst("insideH"))
        val vertical = parseSingleBorder(tblBorders.wFirst("insideV"))
        val result = CellBorders(
            top = horizontal,
            right = vertical,
            bottom = horizontal,
            left = vertical
        )
        return result.takeIf { it.hasAny() }
    }

    private fun parseSingleBorder(element: Element?): Border? {
        if (element == null) return null
        val style = element.wAttr("val")
        if (style != null && NO_PAINT_STYLES.contains(style.lowercase())) {
            return Border(style = style.lowercase(), widthPoints = 0.0)
        }
        return Border(
            style = style,
            widthPoints = (element.wAttr("sz")?.toIntOrNull() ?: 0) / 8.0,
            color = element.wAttr("color")?.takeUnless { it.equals("auto", true) },
            space = Units.twipsToPoints(element.wAttr("space")?.toIntOrNull())
        )
    }

    /** Border side lookup helper for callers merging per-side values. */
    fun side(borders: CellBorders, side: TableBorderSide): Border? = borders.side(side)

    /** Parses a `w:*CellMar` container (`tblCellMar`, `tcMar`) into per-side widths. */
    fun parseCellMargins(parent: Element?): CellBorders? {
        if (parent == null) return null
        fun margin(name: String): Border? {
            val element = parent.wFirst(name) ?: return null
            return Border(
                style = element.wAttr("type"),
                widthPoints = Units.twipsToPoints(element.wAttr("w")?.toIntOrNull())
            )
        }
        val margins = CellBorders(
            top = margin("top"),
            right = margin("right"),
            bottom = margin("bottom"),
            left = margin("left")
        )
        return margins.takeIf { it.hasAny() }
    }
}
