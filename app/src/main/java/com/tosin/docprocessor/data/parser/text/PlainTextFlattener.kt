package com.tosin.docprocessor.data.parser.text

import com.tosin.docprocessor.data.common.model.DocumentElement

/**
 * Flattens a list of [DocumentElement] into plain text used for .txt import/export
 * and fallback paths. Lives in the parser layer so repositories never own text conversion.
 */
object PlainTextFlattener {

    fun toPlainText(elements: List<DocumentElement>): String =
        elements.joinToString("\n") { element ->
            when (element) {
                is DocumentElement.Paragraph -> buildString {
                    element.listLabel?.let {
                        append(it)
                        append(' ')
                    }
                    append(element.spans.joinToString("") { it.text })
                }
                is DocumentElement.SectionHeader -> element.text
                is DocumentElement.Section -> element.properties.toString()
                is DocumentElement.HeaderFooter -> element.content.text
                is DocumentElement.Note -> element.info.text
                is DocumentElement.Comment -> element.info.text
                is DocumentElement.Bookmark -> element.info.name
                is DocumentElement.Field -> element.info.instruction
                is DocumentElement.Metadata -> "${element.info.title ?: element.info.kind}: ${element.info.summary}"
                is DocumentElement.Drawing -> element.info.kind
                is DocumentElement.EmbeddedObject -> element.info.description ?: element.info.kind
                is DocumentElement.Table -> element.rows.joinToString("\n") { row -> row.joinToString("\t") }
                is DocumentElement.Image -> element.caption ?: element.altText.orEmpty()
                DocumentElement.PageBreak -> ""
            }
        }
}