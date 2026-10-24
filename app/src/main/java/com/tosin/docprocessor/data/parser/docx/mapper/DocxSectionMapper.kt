package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.common.w
import com.tosin.docprocessor.data.parser.common.wAttr
import com.tosin.docprocessor.data.parser.common.wFirst
import com.tosin.docprocessor.data.parser.docx.opc.OpcPackage
import com.tosin.docprocessor.data.parser.docx.resolve.DocxNumberingResolver
import com.tosin.docprocessor.data.parser.docx.resolve.DocxSectionProperties
import com.tosin.docprocessor.data.parser.docx.resolve.DocxStyleResolver
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.CommentDefinition
import com.tosin.docprocessor.domain.document.model.DocumentSettings
import com.tosin.docprocessor.domain.document.model.HeaderFooterKind
import com.tosin.docprocessor.domain.document.model.HeaderFooterStory
import com.tosin.docprocessor.domain.document.model.NoteKind
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.HeaderFooterVariant
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Maps `w:sectPr`, `w:settings`, headers/footers, comments and notes. */
class DocxSectionMapper(
    private val opc: OpcPackage,
    private val mainPart: String,
    private val styleResolver: DocxStyleResolver,
    private val numbering: DocxNumberingResolver,
    private val resources: ResourceCollector,
    private val diagnostics: DiagnosticsCollector
) {

    fun settingsDocument(): Document? {
        val path = opc.partByRelationshipType(mainPart, "settings") ?: "word/settings.xml"
        return opc.document(path)
    }

    fun parseSettings(document: Document?): DocumentSettings {
        val root = document?.documentElement ?: return DocumentSettings()
        val protection = root.wFirst("documentProtection")
        val trackChanges = root.wFirst("trackChanges") != null
        val evenOdd = root.wFirst("evenAndOddHeaders") != null
        val defaultTab = Units.twipsToPoints(root.wFirst("defaultTabStop")?.wAttr("val")?.toIntOrNull())
        val view = linkedMapOf<String, String>()
        root.wFirst("view")?.wAttr("val")?.let { view["view"] = it }
        root.wFirst("zoom")?.wAttr("percent")?.let { view["zoomPercent"] = it }
        return DocumentSettings(
            isProtected = protection?.wAttr("enforcement")?.let { it == "1" || it == "true" } == true ||
                (protection != null && protection.wAttr("enforcement") == null),
            protectionMode = protection?.wAttr("edit"),
            trackChanges = trackChanges,
            evenAndOddHeaders = evenOdd,
            defaultTabStopPoints = defaultTab,
            view = view
        )
    }

    fun collectSections(body: Element, settings: DocumentSettings): List<SectionStyle> {
        val sectPrs = mutableListOf<Element>()
        body.w("p").forEach { paragraph ->
            paragraph.wFirst("pPr")?.wFirst("sectPr")?.let(sectPrs::add)
        }
        body.wFirst("sectPr")?.let(sectPrs::add)
        return sectPrs.mapIndexed { index, sectPr ->
            DocxSectionProperties.parse(sectPr, index + 1, settings.evenAndOddHeaders)
        }
    }

    fun parseHeaderFooters(body: Element, settings: DocumentSettings): List<HeaderFooterStory> {
        val sectPrs = mutableListOf<Element>()
        body.w("p").forEach { paragraph ->
            paragraph.wFirst("pPr")?.wFirst("sectPr")?.let(sectPrs::add)
        }
        body.wFirst("sectPr")?.let(sectPrs::add)

        val stories = mutableListOf<HeaderFooterStory>()
        val seen = mutableSetOf<String>()
        sectPrs.forEach { sectPr ->
            parseReferences(sectPr, "headerReference", HeaderFooterKind.HEADER, stories, seen)
            parseReferences(sectPr, "footerReference", HeaderFooterKind.FOOTER, stories, seen)
        }
        return stories
    }

    private fun parseReferences(
        sectPr: Element,
        tag: String,
        kind: HeaderFooterKind,
        stories: MutableList<HeaderFooterStory>,
        seen: MutableSet<String>
    ) {
        sectPr.w(tag).forEach { reference ->
            val relId = reference.wAttr("id") ?: return@forEach
            val target = opc.relationshipsFor(mainPart)[relId]?.target ?: return@forEach
            val variant = variantOf(reference.wAttr("type"))
            val key = "$target|${kind.name}|${variant.name}"
            if (!seen.add(key)) return@forEach
            val context = DocxMapContext(
                part = target,
                opc = opc,
                styles = styleResolver,
                numbering = numbering,
                resources = resources,
                diagnostics = DiagnosticsCollector(target)
            )
            val headerFooterDocument = opc.document(target)
            val root = headerFooterDocument?.documentElement?.wFirst("hdr")
                ?: headerFooterDocument?.documentElement?.wFirst("ftr")
            val blocks = root?.let { DocxBlockMapper(context).also { it.attach(DocxTableMapper(context, it)) }.parseBlocks(it) }
                .orEmpty()
            stories += HeaderFooterStory(
                kind = kind,
                variant = variant,
                blocks = blocks,
                anchor = SourceAnchor(target, tag)
            )
        }
    }

    fun parseComments(document: Document?): Map<String, CommentDefinition> {
        val root = document?.documentElement ?: return emptyMap()
        val part = "word/comments.xml"
        val context = DocxMapContext(part, opc, styleResolver, numbering, resources, DiagnosticsCollector(part))
        val blockMapper = DocxBlockMapper(context).also { it.attach(DocxTableMapper(context, it)) }
        return root.w("comment").mapNotNull { comment ->
            val id = comment.wAttr("id") ?: return@mapNotNull null
            val blocks = blockMapper.parseBlocks(comment)
            id to CommentDefinition(
                id = id,
                author = comment.wAttr("author"),
                initials = comment.wAttr("initials"),
                date = comment.wAttr("date"),
                text = plainText(blocks),
                blocks = blocks
            )
        }.toMap()
    }

    fun parseNotes(document: Document?, kind: NoteKind, part: String): Map<String, BlockNode> {
        val root = document?.documentElement ?: return emptyMap()
        val context = DocxMapContext(part, opc, styleResolver, numbering, resources, DiagnosticsCollector(part))
        val blockMapper = DocxBlockMapper(context).also { it.attach(DocxTableMapper(context, it)) }
        val tag = if (kind == NoteKind.FOOTNOTE) "footnote" else "endnote"
        return root.w(tag).mapNotNull { note ->
            val id = note.wAttr("id") ?: return@mapNotNull null
            if (id.toIntOrNull()?.let { it <= 0 } == true) return@mapNotNull null
            id to BlockNode.NoteBody(
                kind = kind,
                noteId = id,
                blocks = blockMapper.parseBlocks(note),
                anchor = SourceAnchor(part, "w:$tag")
            )
        }.toMap()
    }

    private fun variantOf(type: String?): HeaderFooterVariant = when (type?.lowercase()) {
        "first" -> HeaderFooterVariant.FIRST
        "even" -> HeaderFooterVariant.EVEN
        else -> HeaderFooterVariant.DEFAULT
    }

    private fun plainText(blocks: List<BlockNode>): String = blocks.filterIsInstance<BlockNode.Paragraph>()
        .joinToString("\n") { paragraph ->
            paragraph.inlines.filterIsInstance<com.tosin.docprocessor.domain.document.model.InlineNode.Text>()
                .joinToString("") { it.text }
        }
}
