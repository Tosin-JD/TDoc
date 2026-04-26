package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import org.apache.poi.xwpf.usermodel.XWPFParagraph

class DocxFieldParser {

    fun parse(paragraph: XWPFParagraph): List<DocumentElement.Field> {
        val simpleFields = paragraph.ctp.fldSimpleList.map { simpleField ->
            val instruction = simpleField.instr.orEmpty().trim()
            DocumentElement.Field(
                FieldInfo(
                    type = classifyField(instruction),
                    instruction = instruction,
                    value = simpleField.fldData?.stringValue,
                    isSimpleField = true
                )
            )
        }

        val complexInstruction = paragraph.runs
            .flatMap { it.ctr.instrTextList }
            .joinToString(separator = "") { it.stringValue.orEmpty() }
            .trim()

        val complexFields = if (complexInstruction.isNotEmpty()) {
            listOf(
                DocumentElement.Field(
                    FieldInfo(
                        type = classifyField(complexInstruction),
                        instruction = complexInstruction,
                        value = paragraph.text,
                        isSimpleField = false
                    )
                )
            )
        } else {
            emptyList()
        }

        return simpleFields + complexFields
    }

    private fun classifyField(instruction: String): String {
        val upper = instruction.trim().uppercase()
        return when {
            upper.startsWith("TOC") -> "toc"
            upper.startsWith("PAGE") -> "page-number"
            upper.startsWith("DATE") || upper.startsWith("TIME") -> "date-time"
            upper.startsWith("AUTHOR") -> "author"
            upper.startsWith("TITLE") -> "title"
            upper.startsWith("MERGEFIELD") -> "merge-field"
            upper.startsWith("FORMULA") || upper.startsWith("=") -> "formula"
            upper.startsWith("HYPERLINK") -> "hyperlink"
            upper.startsWith("IF ") -> "conditional"
            else -> "field"
        }
    }
}
