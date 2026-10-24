package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture.capture
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.data.parser.common.rAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.FrameAnchor
import com.tosin.docprocessor.domain.document.model.style.FrameStyle
import org.w3c.dom.Element

/** Maps DrawingML (`w:drawing`) and legacy VML (`w:pict`) into domain nodes. */
class DocxDrawingMapper(
    private val context: DocxMapContext,
    private val blockMapper: DocxBlockMapper? = null
) {

    fun parseDrawing(drawing: Element): InlineNode {
        val anchorNode = drawing.firstNs(Ns.WP, "inline") ?: drawing.firstNs(Ns.WP, "anchor")
        val docPr = drawing.descendantsNs(Ns.WP, "docPr").firstOrNull()
        val extent = drawing.descendantsNs(Ns.WP, "extent").firstOrNull()
        val graphicFrame = drawing.descendantsNs(Ns.A, "graphic").firstOrNull()

        val chart = drawing.descendantsNs("http://schemas.openxmlformats.org/drawingml/2006/chart", "chart")
            .firstOrNull()
        val isChart = chart != null
        val diagram = drawing.descendantsNs("http://schemas.openxmlformats.org/drawingml/2006/diagram", "relIds")
            .isNotEmpty() || drawing.descendantsNs(
            "http://schemas.openxmlformats.org/drawingml/2006/diagram", "dataModelExt"
        ).isNotEmpty()

        val blip = drawing.descendantsNs(Ns.A, "blip").firstOrNull()
        val pic = drawing.descendantsNs(Ns.PIC, "pic").isNotEmpty()

        if (isChart || diagram) {
            context.diagnostics.preserve("w:drawing", "[chart/smartart]")
            val chartTitle = chart?.rAttr("id")
                ?.let { context.relationships()[it]?.target }
                ?.let(context.opc::document)
                ?.documentElement
                ?.descendantsNs("http://schemas.openxmlformats.org/drawingml/2006/chart", "title")
                ?.firstOrNull()
                ?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            return InlineNode.Opaque(
                capture(
                    drawing,
                    SourceAnchor(context.part, "w:drawing"),
                    reason = when {
                        isChart && chartTitle != null -> "chart not rendered; title: $chartTitle"
                        isChart -> "chart not rendered"
                        else -> "smartart not rendered"
                    }
                )
            )
        }

        if (blip == null || (!pic && graphicFrame == null)) {
            // Shapes may carry a text payload (`wps:txbx` > `w:txbxContent`).
            val txbxContent = drawing.descendantsNs(Ns.W, "txbxContent").firstOrNull()
            if (txbxContent != null && blockMapper != null) {
                context.diagnostics.info("w:drawing", "Mapped shape with text box")
                return textBox(txbxContent, drawingFrameStyle(anchorNode, extent), "w:txbxContent", blockMapper)
            }
            context.diagnostics.preserve("w:drawing", "[shape]")
            return InlineNode.Opaque(capture(drawing, SourceAnchor(context.part, "w:drawing"), "shape not rendered"))
        }

        val embedId = blip.rAttr("embed")
        val linkId = blip.rAttr("link")
        val relationship = (embedId ?: linkId)?.let { context.relationships()[it] }
        val resourceId = relationship?.let { rel ->
            if (rel.isExternal) {
                context.resources.add(rel.target, isExternal = true)
            } else {
                context.resources.add(rel.target, bytes = context.opc.part(rel.target))
            }
        }

        val rotationRaw = drawing.descendantsNs(Ns.A, "xfrm").firstOrNull()?.attrNs(Ns.A, "rot")?.toIntOrNull()
        context.diagnostics.info("w:drawing", "Mapped drawing/image")

        return InlineNode.Drawing(
            frameStyle = drawingFrameStyle(anchorNode, extent)
                .copy(rotationDegrees = rotationRaw?.let { it / 60_000.0 }),
            resourceId = resourceId,
            altText = docPr?.getAttribute("descr")?.takeIf { it.isNotBlank() }
                ?: docPr?.getAttribute("title")?.takeIf { it.isNotBlank() },
            title = docPr?.getAttribute("title")?.takeIf { it.isNotBlank() },
            anchor = SourceAnchor(context.part, "w:drawing")
        )
    }

    /** Extent, anchor kind, absolute position, wrap style and z-order of a drawing. */
    private fun drawingFrameStyle(anchorNode: Element?, extent: Element?): FrameStyle {
        val isInline = anchorNode != null && anchorNode.matchesNs(Ns.WP, "inline")
        val positionH = anchorNode?.firstNs(Ns.WP, "positionH")
        val positionV = anchorNode?.firstNs(Ns.WP, "positionV")
        return FrameStyle(
            anchor = if (isInline) FrameAnchor.INLINE else FrameAnchor.ANCHORED,
            // wp:extent/posOffset values are unprefixed attributes → no namespace.
            widthPoints = Units.emuToPoints(extent?.unprefixedLong("cx")),
            heightPoints = Units.emuToPoints(extent?.unprefixedLong("cy")),
            positionXPoints = if (isInline) null else Units.emuToPoints(
                positionH?.firstNs(Ns.WP, "posOffset")?.textContent?.trim()?.toLongOrNull()
            ),
            positionYPoints = if (isInline) null else Units.emuToPoints(
                positionV?.firstNs(Ns.WP, "posOffset")?.textContent?.trim()?.toLongOrNull()
            ),
            wrapStyle = wrapStyleOf(anchorNode),
            zOrder = anchorNode?.unprefixedLong("relativeHeight")?.toInt()
        )
    }

    private fun Element.unprefixedLong(local: String): Long? =
        getAttribute(local).takeIf { it.isNotEmpty() }?.toLongOrNull()

    private fun wrapStyleOf(anchorNode: Element?): String? {
        val children = anchorNode?.childNodes ?: return null
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            val local = child.localName ?: continue
            when (local) {
                "wrapNone" -> return "none"
                "wrapSquare" -> return "square"
                "wrapTight" -> return "tight"
                "wrapThrough" -> return "through"
                "wrapTopAndBottom" -> return "topAndBottom"
            }
        }
        return null
    }

    fun parseVmlPicture(pict: Element): InlineNode {
        val shape = pict.descendantsNs(Ns.VML, "shape").firstOrNull()
        val textbox = pict.descendantsNs(Ns.VML, "textbox").firstOrNull()
        val txbxContent = textbox?.wFirst("txbxContent")
        if (txbxContent != null && blockMapper != null) {
            context.diagnostics.info("w:pict", "Mapped VML text box")
            return textBox(txbxContent, vmlFrameStyle(shape, textbox), "v:textbox", blockMapper)
        }
        val imagedata = pict.descendantsNs(Ns.VML, "imagedata").firstOrNull()
        val relId = imagedata?.rAttr("id") ?: imagedata?.rAttr("embed")
        val relationship = relId?.let { context.relationships()[it] }
        val resourceId = relationship?.let { rel ->
            context.resources.add(rel.target, bytes = context.opc.part(rel.target))
        }
        if (resourceId == null) {
            context.diagnostics.preserve("w:pict", "[vml]")
            return InlineNode.Opaque(capture(pict, SourceAnchor(context.part, "w:pict"), "VML shape not rendered"))
        }
        return InlineNode.Drawing(
            resourceId = resourceId,
            altText = shape?.getAttribute("alt")?.takeIf { it.isNotBlank() },
            title = shape?.getAttribute("title")?.takeIf { it.isNotBlank() },
            anchor = SourceAnchor(context.part, "w:pict")
        )
    }

    /**
     * Maps `w:txbxContent` blocks into an [InlineNode.TextBox], capping nesting
     * depth so hostile/broken documents can't overflow the stack (F7).
     */
    private fun textBox(
        boxContent: Element,
        frameStyle: FrameStyle,
        construct: String,
        mapper: DocxBlockMapper
    ): InlineNode {
        if (context.textBoxDepth >= TEXT_BOX_DEPTH_CAP) {
            context.diagnostics.warning(construct, "text box nesting exceeds $TEXT_BOX_DEPTH_CAP; preserved opaque")
            return InlineNode.Opaque(
                capture(boxContent, SourceAnchor(context.part, construct), "text box nesting too deep")
            )
        }
        context.textBoxDepth++
        try {
            return InlineNode.TextBox(
                blocks = mapper.parseBlocks(boxContent),
                frameStyle = frameStyle,
                anchor = SourceAnchor(context.part, construct)
            )
        } finally {
            context.textBoxDepth--
        }
    }

    /** VML shape geometry comes from its `style` attribute (`width:…;height:…`). */
    private fun vmlFrameStyle(shape: Element?, textbox: Element?): FrameStyle {
        val styleText = shape?.getAttribute("style")?.takeIf { it.isNotBlank() }
            ?: textbox?.getAttribute("style")?.takeIf { it.isNotBlank() }
        return FrameStyle(
            anchor = FrameAnchor.ANCHORED,
            widthPoints = vmlLength(styleText, "width"),
            heightPoints = vmlLength(styleText, "height")
        )
    }

    private fun vmlLength(styleText: String?, key: String): Double? {
        val entry = styleText?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$key:") } ?: return null
        return Units.parseOdfLength(entry.removePrefix("$key:").trim())
    }

    private companion object {
        /** Maximum nesting of text boxes inside text boxes (guards the recursion). */
        const val TEXT_BOX_DEPTH_CAP = 8
    }
}
