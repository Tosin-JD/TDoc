package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import org.apache.poi.xwpf.usermodel.XWPFParagraph

class DocxParagraphParser(
    private val listParser: DocxListParser = DocxListParser()
) {
    fun parse(poiParagraph: XWPFParagraph): DocumentElement.Paragraph {
        val label = listParser.getListLabel(poiParagraph)

        val spans = poiParagraph.runs.map { run ->
            TextSpan(
                text = run.getText(0) ?: "",
                isBold = run.isBold,
                isItalic = run.isItalic,
                color = run.color ?: "000000"
            )
        }

        return DocumentElement.Paragraph(spans = spans, listLabel = label)
    }
}