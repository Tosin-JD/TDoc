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
                output += DocumentElement.Drawing(
                    DrawingInfo(
                        kind = "drawing",
                        isInline = true,
                        widthEmu = inline.extent?.cx,
                        heightEmu = inline.extent?.cy,
                        altText = inline.docPr?.descr,
                        title = inline.docPr?.title,
                        wrapStyle = "inline",
                        hasChart = inline.xmlText().contains("c:chart"),
                        hasSmartArt = inline.xmlText().contains("dgm:")
                    )
                )
            }
            drawing.anchorList.forEach { anchor ->
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
                        wrapStyle = resolveWrapStyle(anchor.xmlText()),
                        hasChart = anchor.xmlText().contains("c:chart"),
                        hasSmartArt = anchor.xmlText().contains("dgm:")
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
}
