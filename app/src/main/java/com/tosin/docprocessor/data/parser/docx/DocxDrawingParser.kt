package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.DrawingInfo
import com.tosin.docprocessor.data.parser.internal.models.EmbeddedObjectInfo
import org.apache.poi.xwpf.usermodel.XWPFRun

class DocxDrawingParser {

    fun parse(run: XWPFRun): List<DocumentElement> {
        val ctr = run.ctr
        val output = mutableListOf<DocumentElement>()

        ctr.drawingList.forEach { drawing ->
            drawing.inlineList.forEach { inline ->
                val xml = inline.xmlText()
                output += DocumentElement.Drawing(
                    DrawingInfo(
                        kind = "drawing",
                        isInline = true,
                        widthEmu = inline.extent?.cx,
                        heightEmu = inline.extent?.cy,
                        altText = inline.docPr?.descr,
                        title = inline.docPr?.title,
                        wrapStyle = "inline",
                        hasChart = xml.contains("c:chart"),
                        hasSmartArt = xml.contains("dgm:"),
                        rotation = extractRotation(xml),
                        effects = extractEffects(xml)
                    )
                )
            }
            drawing.anchorList.forEach { anchor ->
                val xml = anchor.xmlText()
                output += DocumentElement.Drawing(
                    DrawingInfo(
                        kind = "drawing",
                        isInline = false,
                        widthEmu = anchor.extent?.cx,
                        heightEmu = anchor.extent?.cy,
                        altText = anchor.docPr?.descr,
                        title = anchor.docPr?.title,
                        positionX = anchor.positionH?.relativeFrom?.toString(),
                        positionY = anchor.positionV?.relativeFrom?.toString(),
                        wrapStyle = resolveWrapStyle(xml),
                        hasChart = xml.contains("c:chart"),
                        hasSmartArt = xml.contains("dgm:"),
                        rotation = extractRotation(xml),
                        effects = extractEffects(xml),
                        isGrouped = xml.contains("<wps:grpSp") || xml.contains("<v:group"),
                        hasText = xml.contains("<w:txbx") || xml.contains("<v:textbox")
                    )
                )
            }
        }

        ctr.pictList.forEach { pict ->
            output += DocumentElement.Drawing(
                DrawingInfo(
                    kind = "pict",
                    isInline = true,
                    altText = pict.xmlText().substringAfter("alt=\"", "").substringBefore("\"", ""),
                    title = null,
                    wrapStyle = "legacy-vml"
                )
            )
        }

        ctr.objectList.forEach { obj ->
            val xml = obj.xmlText()
            output += DocumentElement.EmbeddedObject(
                EmbeddedObjectInfo(
                    kind = "ole-object",
                    programId = xml.substringAfter("ProgID=\"", "").substringBefore("\"", ""),
                    shapeId = obj.dxaOrig?.toString(),
                    description = xml.take(200)
                )
            )
        }

        return output
    }

    private fun resolveWrapStyle(xml: String): String =
        when {
            "wrapSquare" in xml -> "square"
            "wrapTight" in xml -> "tight"
            "wrapThrough" in xml -> "through"
            "behindDoc" in xml -> "behind"
            "wrapTopAndBottom" in xml -> "top-bottom"
            else -> "floating"
        }

    private fun extractRotation(xml: String): Int? {
        val rotAttr = xml.substringAfter(" rot=\"", "").substringBefore("\"", "")
        return rotAttr.toIntOrNull()?.let { it / 60000 } // DrawingML rotation is in 60000ths of a degree
    }

    private fun extractEffects(xml: String): List<String> {
        val effects = mutableListOf<String>()
        if ("<a:outerShdw" in xml) effects += "shadow"
        if ("<a:reflection" in xml) effects += "reflection"
        if ("<a:glow" in xml) effects += "glow"
        if ("<a:softEdge" in xml) effects += "soft-edge"
        if ("<a:scene3d" in xml) effects += "3d-scene"
        if ("<a:sp3d" in xml) effects += "3d-shape"
        return effects
    }
}
