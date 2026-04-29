package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.FieldInfo
import org.apache.poi.xwpf.usermodel.XWPFParagraph

class DocxFieldParser {

    fun parse(paragraph: XWPFParagraph): List<DocumentElement.Field> {
        val simpleFields = paragraph.ctp.fldSimpleList.map { simpleField ->
            val instruction = simpleField.instr.orEmpty().trim()
            DocumentElement.Field(
                info = FieldInfo(
                    type = classifyField(instruction),
                    instruction = instruction,
                    value = simpleField.fldData?.stringValue,
                    isSimpleField = true,
                    arguments = extractArguments(instruction),
                    source = "fldSimple"
                )
            )
        }

        val complexFields = parseComplexFields(paragraph)

        return (simpleFields + complexFields).distinctBy { "${it.info.type}:${it.info.instruction}:${it.info.source}" }
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
            upper.startsWith("SET") -> "variable-set"
            upper.startsWith("REF") -> "variable-ref"
            upper.startsWith("SEQ") -> "sequence"
            upper.startsWith("ASK") -> "prompt-ask"
            upper.startsWith("FILLIN") -> "prompt-fillin"
            upper.startsWith("MACROBUTTON") -> "macro-button"
            upper.startsWith("FORMTEXT") -> "form-text"
            upper.startsWith("FORMCHECKBOX") -> "form-checkbox"
            upper.startsWith("FORMDROPDOWN") -> "form-dropdown"
            upper.startsWith("STYLEREF") -> "style-reference"
            upper.startsWith("XE ") -> "index-entry"
            upper.startsWith("TC ") -> "table-of-contents-entry"
            else -> "field"
        }
    }

    private fun parseComplexFields(paragraph: XWPFParagraph): List<DocumentElement.Field> {
        val instructions = mutableListOf<String>()
        val current = StringBuilder()
        var insideField = false

        paragraph.runs.forEach { run ->
            val hasBegin = run.ctr.fldCharList.any { it.fldCharType?.toString()?.equals("begin", ignoreCase = true) == true }
            val hasEnd = run.ctr.fldCharList.any { it.fldCharType?.toString()?.equals("end", ignoreCase = true) == true }

            if (hasBegin) {
                if (current.isNotBlank()) {
                    instructions += current.toString().trim()
                    current.clear()
                }
                insideField = true
            }

            run.ctr.instrTextList
                .map { it.stringValue.orEmpty() }
                .filter { it.isNotBlank() }
                .forEach { text ->
                    if (insideField) {
                        current.append(text)
                    } else {
                        instructions += text.trim()
                    }
                }

            if (hasEnd) {
                current.toString().trim().takeIf { it.isNotBlank() }?.let(instructions::add)
                current.clear()
                insideField = false
            }
        }

        current.toString().trim().takeIf { it.isNotBlank() }?.let(instructions::add)

        return instructions.map { instruction ->
            DocumentElement.Field(
                info = FieldInfo(
                    type = classifyField(instruction),
                    instruction = instruction,
                    value = paragraph.text,
                    isSimpleField = false,
                    arguments = extractArguments(instruction),
                    source = "instrText"
                )
            )
        }
    }

    private fun extractArguments(instruction: String): Map<String, String> {
        val normalized = instruction.trim()
        if (normalized.isBlank()) {
            return emptyMap()
        }

        val tokens = normalized.split(Regex("\\s+"))
        val command = tokens.firstOrNull().orEmpty().uppercase()
        return buildMap {
            when {
                command == "HYPERLINK" -> {
                    normalized.substringAfter("HYPERLINK", "").trim().trim('"').takeIf { it.isNotBlank() }?.let {
                        put("target", it)
                    }
                }
                command == "MERGEFIELD" -> {
                    tokens.getOrNull(1)?.trim('"')?.takeIf { it.isNotBlank() }?.let { put("fieldName", it) }
                }
                command == "IF" -> {
                    put("condition", normalized.removePrefix(tokens.firstOrNull().orEmpty()).trim())
                }
                command == "TOC" -> {
                    put("switches", tokens.drop(1).joinToString(" "))
                }
                command == "PAGE" || command == "NUMPAGES" -> {
                    put("pageField", command)
                }
                command == "DATE" || command == "TIME" || command == "AUTHOR" || command == "TITLE" -> {
                    put("property", command.lowercase())
                }
                normalized.startsWith("=") || command == "FORMULA" -> {
                    put("expression", normalized.removePrefix("FORMULA").trim())
                }
                command == "SET" || command == "REF" -> {
                    tokens.getOrNull(1)?.trim('"')?.let { put("bookmark", it) }
                    if (command == "SET") {
                        put("value", tokens.drop(2).joinToString(" ").trim('"'))
                    }
                }
                command == "SEQ" -> {
                    tokens.getOrNull(1)?.let { put("sequenceIdentifier", it) }
                }
                command == "MACROBUTTON" -> {
                    tokens.getOrNull(1)?.let { put("macroName", it) }
                    put("displayText", tokens.drop(2).joinToString(" "))
                }
                command.startsWith("FORM") -> {
                    put("formType", command)
                }
                command == "STYLEREF" -> {
                    tokens.getOrNull(1)?.trim('"')?.let { put("styleName", it) }
                }
            }
        }
    }
}
