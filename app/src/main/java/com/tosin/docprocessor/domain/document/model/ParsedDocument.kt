package com.tosin.docprocessor.domain.document.model

import com.tosin.docprocessor.domain.document.CompatibilityLevel
import com.tosin.docprocessor.domain.document.ParseDiagnostic
import com.tosin.docprocessor.domain.document.model.style.HeaderFooterVariant
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextStyle

enum class SourceFormat { DOCX, ODT, UNKNOWN }

enum class HeaderFooterKind { HEADER, FOOTER }

data class DocumentMetadata(
    val title: String? = null,
    val subject: String? = null,
    val creator: String? = null,
    val lastModifiedBy: String? = null,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val category: String? = null,
    val contentStatus: String? = null,
    val created: String? = null,
    val modified: String? = null,
    val revision: String? = null,
    val application: String? = null,
    val company: String? = null,
    val template: String? = null,
    val words: Int? = null,
    val pages: Int? = null,
    val characters: Int? = null,
    val paragraphs: Int? = null,
    val language: String? = null,
    val userDefined: Map<String, String> = emptyMap(),
    val raw: Map<String, String> = emptyMap()
)

data class StyleCatalogue(
    val paragraphStyles: Map<String, ParagraphStyle> = emptyMap(),
    val textStyles: Map<String, TextStyle> = emptyMap(),
    val listStyles: Map<String, ListStyle> = emptyMap(),
    val tableStyles: Map<String, TableStyle> = emptyMap(),
    val defaultParagraphStyle: ParagraphStyle = ParagraphStyle(),
    val defaultTextStyle: TextStyle = TextStyle()
)

data class CommentDefinition(
    val id: String,
    val author: String? = null,
    val initials: String? = null,
    val date: String? = null,
    val text: String = "",
    val blocks: List<BlockNode> = emptyList(),
    val parentId: String? = null
)

data class HeaderFooterStory(
    val kind: HeaderFooterKind,
    val variant: HeaderFooterVariant,
    val blocks: List<BlockNode>,
    val anchor: SourceAnchor? = null
)

data class DocumentSettings(
    val isProtected: Boolean = false,
    val protectionMode: String? = null,
    val trackChanges: Boolean = false,
    val evenAndOddHeaders: Boolean = false,
    val defaultTabStopPoints: Double? = null,
    val view: Map<String, String> = emptyMap()
)

data class OutlineEntry(
    val text: String,
    val level: Int,
    val anchor: SourceAnchor? = null
)

/** Root aggregate produced by a format parser. Pure; no Android/XML types. */
data class ParsedDocument(
    val blocks: List<BlockNode> = emptyList(),
    val metadata: DocumentMetadata = DocumentMetadata(),
    val styles: StyleCatalogue = StyleCatalogue(),
    val resources: Map<String, Resource> = emptyMap(),
    val sections: List<SectionStyle> = emptyList(),
    val headerFooters: List<HeaderFooterStory> = emptyList(),
    val comments: Map<String, CommentDefinition> = emptyMap(),
    val notes: Map<String, BlockNode> = emptyMap(),
    val settings: DocumentSettings = DocumentSettings(),
    val sourceFormat: SourceFormat = SourceFormat.UNKNOWN,
    val diagnostics: List<ParseDiagnostic> = emptyList(),
    val compatibility: CompatibilityLevel = CompatibilityLevel.RENDERED_SUPPORTED,
    val opaqueParts: List<OpaquePackagePart> = emptyList(),
    val outline: List<OutlineEntry> = emptyList()
) {
    companion object {
        fun outlineOf(blocks: List<BlockNode>): List<OutlineEntry> = buildOutline(blocks, 0)

        private fun buildOutline(blocks: List<BlockNode>, depth: Int): List<OutlineEntry> {
            val entries = mutableListOf<OutlineEntry>()
            blocks.forEach { block ->
                when (block) {
                    is BlockNode.Heading -> entries += OutlineEntry(
                        text = block.inlines.filterIsInstance<InlineNode.Text>().joinToString("") { it.text },
                        level = block.level,
                        anchor = block.anchor
                    )
                    is BlockNode.Table -> block.rows.forEach { row ->
                        row.cells.forEach { entries += buildOutline(it.blocks, depth + 1) }
                    }
                    is BlockNode.ListBlock -> block.items.forEach { item ->
                        entries += buildOutline(item.blocks, depth + 1)
                    }
                    is BlockNode.ListItem -> entries += buildOutline(block.blocks, depth + 1)
                    is BlockNode.TextBox -> entries += buildOutline(block.blocks, depth + 1)
                    is BlockNode.RevisionBlock -> entries += buildOutline(block.blocks, depth + 1)
                    is BlockNode.NoteBody -> entries += buildOutline(block.blocks, depth + 1)
                    else -> Unit
                }
            }
            return entries
        }
    }
}
