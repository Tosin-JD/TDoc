package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFParagraph

class DocxPageBreakParser {

    fun parse(paragraph: XWPFParagraph): List<DocumentElement.PageBreak> =
        paragraph.runs.flatMap { run ->
            val explicitBreaks = run.ctr.brList.count { it.type?.toString()?.equals("page", ignoreCase = true) == true }
            val renderedBreaks = run.ctr.lastRenderedPageBreakList.size
            List(explicitBreaks + renderedBreaks) { DocumentElement.PageBreak() }
        }
}
