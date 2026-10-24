package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.InlineNode
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.FrameAnchor
import com.tosin.docprocessor.domain.document.model.style.FrameStyle
import org.w3c.dom.Element

/** Maps `draw:frame` (images, text boxes, objects) into domain nodes. */
class OdfFrameMapper(private val context: OdfMapContext) {

    lateinit var blockMapper: OdfBlockMapper
        private set

    fun attach(blockMapper: OdfBlockMapper) {
        this.blockMapper = blockMapper
    }

    fun parseInlineFrame(frame: Element): InlineNode {
        val image = frame.firstNs(Ns.DRAW, "image")
        if (image != null) {
            val resourceId = resolveImage(image)
            if (resourceId != null) {
                return InlineNode.Drawing(
                    frameStyle = frameStyle(frame),
                    resourceId = resourceId,
                    altText = title(frame, "desc"),
                    title = title(frame, "title"),
                    anchor = SourceAnchor(context.part, "draw:frame")
                )
            }
        }
        val inlineTextBox = frame.firstNs(Ns.DRAW, "text-box")
        if (inlineTextBox != null && ::blockMapper.isInitialized) {
            context.diagnostics.info("draw:text-box", "Mapped inline text box")
            return InlineNode.TextBox(
                blocks = blockMapper.parseBlocks(inlineTextBox),
                frameStyle = frameStyle(frame),
                anchor = SourceAnchor(context.part, "draw:text-box")
            )
        }
        return InlineNode.Opaque(opaqueFrame(frame, "inline"))
    }

    fun parseBlockFrame(frame: Element): BlockNode {
        val image = frame.firstNs(Ns.DRAW, "image")
        if (image != null) {
            val resourceId = resolveImage(image)
            if (resourceId != null) {
                return BlockNode.Figure(
                    frameStyle = frameStyle(frame),
                    resourceId = resourceId,
                    caption = frame.firstNs(Ns.DRAW, "caption")?.textContent?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { listOf(InlineNode.Text(it)) }
                        .orEmpty(),
                    altText = title(frame, "desc"),
                    title = title(frame, "title"),
                    anchor = SourceAnchor(context.part, "draw:frame")
                )
            }
        }
        val textBox = frame.firstNs(Ns.DRAW, "text-box")
        if (textBox != null && ::blockMapper.isInitialized) {
            return BlockNode.TextBox(
                blocks = blockMapper.parseBlocks(textBox),
                frameStyle = frameStyle(frame),
                anchor = SourceAnchor(context.part, "draw:text-box")
            )
        }
        return BlockNode.Opaque(opaqueFrame(frame, "block"))
    }

    private fun opaqueFrame(
        frame: Element,
        location: String
    ): com.tosin.docprocessor.domain.document.model.OpaqueNode {
        val chart = frame.firstNs(Ns.DRAW, "chart")
        val objectElement = frame.firstNs(Ns.DRAW, "object")
            ?: frame.firstNs(Ns.DRAW, "object-ole")
        val title = chart?.firstNs(Ns.SVG, "title")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        val classId = objectElement?.attrNs(Ns.DRAW, "class-id")
        val objectName = objectElement?.attrNs(Ns.DRAW, "name")
        val objectMetadata = classId?.let { "class-id=$it" }
            ?: objectName?.let { "name=$it" }
        val reason = when {
            chart != null && title != null -> "chart not rendered; title: $title"
            chart != null -> "chart not rendered"
            objectElement != null && objectMetadata != null -> "embedded object not rendered; $objectMetadata"
            objectElement != null -> "embedded object not rendered"
            location == "inline" -> "frame not rendered inline"
            else -> "frame not rendered"
        }
        context.diagnostics.preserve("draw:frame", location)
        return com.tosin.docprocessor.data.parser.common.OpaqueFragmentCapture.capture(
            frame, SourceAnchor(context.part, "draw:frame"), reason
        )
    }

    private fun resolveImage(image: Element): String? {
        val href = image.attrNs(Ns.XLINK, "href") ?: return null
        val normalized = href.removePrefix("./")
        if (context.entries.containsKey(normalized)) {
            return context.resources.add(normalized, bytes = context.entries[normalized])
        }
        return context.resources.add(href, isExternal = true)
    }

    private fun frameStyle(frame: Element): FrameStyle = FrameStyle(
        anchor = if (frame.attrNs(Ns.TEXT, "anchor-type")?.contains("as-char") == true) {
            FrameAnchor.INLINE
        } else FrameAnchor.ANCHORED,
        widthPoints = Units.parseOdfLength(frame.attrNs(Ns.SVG, "width")),
        heightPoints = Units.parseOdfLength(frame.attrNs(Ns.SVG, "height")),
        positionXPoints = if (frame.attrNs(Ns.TEXT, "anchor-type")?.contains("as-char") == true) null
            else Units.parseOdfLength(frame.attrNs(Ns.SVG, "x")),
        positionYPoints = if (frame.attrNs(Ns.TEXT, "anchor-type")?.contains("as-char") == true) null
            else Units.parseOdfLength(frame.attrNs(Ns.SVG, "y")),
        rotationDegrees = rotationOf(frame),
        wrapStyle = wrapStyleOf(frame),
        zOrder = frame.attrNs(Ns.DRAW, "z-index")?.toIntOrNull()
    )

    /** ODF rotation arrives as part of `draw:transform` (`rotate(x)` in degrees). */
    private fun rotationOf(frame: Element): Double? {
        val transform = frame.attrNs(Ns.DRAW, "transform") ?: return null
        return Regex("""rotate\(\s*(-?[0-9.]+)""").find(transform)
            ?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Wrap via the `draw:wrap-*` child elements; style `style:wrap` is a fallback. */
    private fun wrapStyleOf(frame: Element): String? {
        val children = listOf(
            "wrap-none" to "none",
            "wrap-off" to "none",
            "wrap-square" to "square",
            "wrap-parallel" to "parallel",
            "wrap-top-and-bottom" to "topAndBottom",
            "wrap-through" to "through"
        )
        for ((local, style) in children) {
            if (frame.firstNs(Ns.DRAW, local) != null) return style
        }
        return frame.attrNs(Ns.STYLE, "wrap")
    }

    private fun title(frame: Element, local: String): String? =
        frame.firstNs(Ns.SVG, local)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: frame.descendantsNs(Ns.SVG, local).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
