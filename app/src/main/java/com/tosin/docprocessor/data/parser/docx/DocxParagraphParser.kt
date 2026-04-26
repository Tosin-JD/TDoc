package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.HyperlinkInfo
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink

class DocxParagraphParser(
    private val listParser: DocxListParser = DocxListParser(),
    private val styleParser: DocxParagraphStyleParser = DocxParagraphStyleParser()
) {
    fun parse(poiParagraph: XWPFParagraph): DocumentElement.Paragraph {
        val label = listParser.getListLabel(poiParagraph)
        val listInfo = listParser.parseListInfo(poiParagraph)
        val style = styleParser.parse(poiParagraph)

        val spans = poiParagraph.runs.mapNotNull { run ->
            val text = run.text().orEmpty()
            if (text.isEmpty()) {
                return@mapNotNull null
            }
            val verticalAlignment = run.verticalAlignment?.toString()?.lowercase()
            TextSpan(
                text = text,
                isBold = run.isBold,
                isItalic = run.isItalic,
                isUnderline = run.underline != UnderlinePatterns.NONE,
                isStrikethrough = run.isStrikeThrough,
                isSuperscript = verticalAlignment == "superscript",
                isSubscript = verticalAlignment == "subscript",
                isHidden = run.isVanish,
                fontFamily = run.fontFamily,
                fontSize = run.fontSize.takeIf { it > 0 },
                color = run.color ?: "000000",
                highlightColor = run.textHighlightColor?.toString(),
                characterSpacing = run.characterSpacing.takeIf { it != 0 },
                language = run.lang,
                hasShadow = run.isShadowed,
                hasOutline = false,
                isEmbossed = run.isEmbossed,
                isEngraved = run.isImprinted
            )
        }

        val hyperlink = poiParagraph.iRuns
            .filterIsInstance<XWPFHyperlinkRun>()
            .firstNotNullOfOrNull { run ->
                run.toHyperlinkInfo(poiParagraph)
            } ?: poiParagraph.ctp.hyperlinkList.firstOrNull()?.toHyperlinkInfo(poiParagraph)

        return DocumentElement.Paragraph(
            spans = spans,
            listLabel = label,
            style = style,
            hyperlink = hyperlink,
            listInfo = listInfo
        )
    }

    private fun XWPFHyperlinkRun.toHyperlinkInfo(paragraph: XWPFParagraph): HyperlinkInfo {
        val linked = getHyperlink(paragraph.document)
        return HyperlinkInfo(
            address = linked?.url,
            anchor = anchor,
            tooltip = ctHyperlink.tooltip,
            docLocation = ctHyperlink.docLocation
        )
    }

    private fun CTHyperlink.toHyperlinkInfo(paragraph: XWPFParagraph): HyperlinkInfo {
        val linked = id?.let { paragraph.document.getHyperlinkByID(it) }
        return HyperlinkInfo(
            address = linked?.url,
            anchor = anchor,
            tooltip = tooltip,
            docLocation = docLocation
        )
    }
}
