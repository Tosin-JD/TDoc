package com.tosin.docprocessor.data.parser.docx.resolve

import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.Border
import com.tosin.docprocessor.domain.document.model.style.CellBorders
import org.w3c.dom.Element

/** Shared WordprocessingML border/margin parsing used by table + style mappers. */
object DocxBorderProperties {

    /** Parses a `w:*Borders` container (`tblBorders`, `tcBorders`, `pBdr`-like). */
    fun parseCellBorders(parent: Element?): CellBorders {
        if (parent == null) return CellBorders()
        fun border(name: String): Border? {
            val element = parent.wFirst(name) ?: return null
            if (element.wAttr("val") == "nil") return null
            return Border(
                style = element.wAttr("val"),
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
        return margins.takeIf {
            it.top != null || it.right != null || it.bottom != null || it.left != null
        }
    }
}
