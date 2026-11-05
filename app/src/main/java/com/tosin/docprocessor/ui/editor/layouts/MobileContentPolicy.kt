package com.tosin.docprocessor.ui.editor.layouts

import com.tosin.docprocessor.domain.model.DocumentElement

/**
 * Decides which flat [DocumentElement]s belong on the mobile reading surface.
 *
 * Mobile is a reflowing reading/editing document, not a miniature printed page:
 *
 * - **Body content** (paragraphs, headings, tables, images, unsupported-object
 *   placeholders) is rendered in document order.
 * - **Layout control data** (`Section` page setup, `PageBreak`, `HeaderFooter`
 *   stories) shapes print pagination only. It must never appear as text and
 *   never become an empty row in the reading list.
 * - **Metadata/annotation records** (`Metadata`, `Field`, `Bookmark`, `Comment`,
 *   `Note`) are not body text. They stay available to diagnostics/print and are
 *   omitted here rather than dumped at the top of the document.
 */
object MobileContentPolicy {

    fun isBodyContent(element: DocumentElement): Boolean = when (element) {
        is DocumentElement.Paragraph -> true
        is DocumentElement.Table -> element.rows.isNotEmpty()
        is DocumentElement.Image -> true
        is DocumentElement.SectionHeader -> element.text.isNotBlank()
        is DocumentElement.Drawing -> true
        is DocumentElement.EmbeddedObject -> true

        is DocumentElement.Section,
        is DocumentElement.PageBreak,
        is DocumentElement.HeaderFooter,
        is DocumentElement.Metadata,
        is DocumentElement.Field,
        is DocumentElement.Bookmark,
        is DocumentElement.Comment,
        is DocumentElement.Note -> false
    }

    /** Visible text for an element, or `null` when it body-renders nothing. */
    fun visibleText(element: DocumentElement): String? = when (element) {
        is DocumentElement.Paragraph -> buildString {
            element.listLabel?.let { append(it).append(' ') }
            element.spans.forEach { append(it.text) }
        }

        is DocumentElement.SectionHeader -> element.text
        is DocumentElement.Table -> element.rows.joinToString("\n") { it.joinToString("\t") }
        else -> null
    }
}
