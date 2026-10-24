package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.data.parser.common.matchesNs
import com.tosin.docprocessor.domain.document.model.BlockNode
import com.tosin.docprocessor.domain.document.model.DocumentSettings
import com.tosin.docprocessor.domain.document.model.HeaderFooterKind
import com.tosin.docprocessor.domain.document.model.HeaderFooterStory
import com.tosin.docprocessor.domain.document.model.SourceAnchor
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.HeaderFooterVariant
import com.tosin.docprocessor.domain.document.model.style.SectionStyle
import org.w3c.dom.Element

/** Extracts settings, master-page sections and header/footer stories. */
class OdfSectionMapper(
    private val context: OdfMapContext,
    private val blockMapper: OdfBlockMapper
) {

    fun parseSettings(settingsRoot: Element?): DocumentSettings {
        if (settingsRoot == null) return DocumentSettings()
        val view = linkedMapOf<String, String>()
        settingsRoot.descendantsNs(Ns.CONFIG, "config-item").forEach { item ->
            val name = item.attrNs(Ns.CONFIG, "name") ?: return@forEach
            view[name] = item.textContent?.trim().orEmpty()
        }
        return DocumentSettings(
            isProtected = view.containsKey("ProtectionKey"),
            protectionMode = view.keys.firstOrNull { it.startsWith("Protection") },
            trackChanges = view["TrackChanges"]?.equals("true", ignoreCase = true) == true,
            defaultTabStopPoints = parseConfigLength(
                view["DefaultTabStop"] ?: view["DefaultTabStopDistance"]
            ),
            view = view
        )
    }

    private fun parseConfigLength(raw: String?): Double? =
        Units.parseOdfLength(raw) ?: raw?.trim()?.removeSuffix("\"")
            ?.toDoubleOrNull()?.let { Units.inchesToPoints(it) }

    fun collectSections(stylesRoot: Element?): List<SectionStyle> {
        if (stylesRoot == null) return emptyList()
        return stylesRoot.descendantsNs(Ns.STYLE, "master-page").mapIndexed { index, master ->
            val masterName = master.attrNs(Ns.STYLE, "name")
            val basePage = context.styles.pageStyle(master.attrNs(Ns.STYLE, "page-layout-name"))
            val backgroundImage = master.descendantsNs(Ns.STYLE, "background-image").firstOrNull()
                ?.attrNs(Ns.XLINK, "href")
                ?.let { href ->
                    val path = href.removePrefix("./")
                    if (context.entries.containsKey(path)) {
                        context.resources.add(path, bytes = context.entries[path])
                    } else {
                        context.resources.add(href, isExternal = true)
                    }
                }
            SectionStyle(
                index = index,
                type = "nextPage",
                page = basePage.copy(backgroundImageResourceId = backgroundImage),
                pageNumberStart = master.attrNs(Ns.STYLE, "page-number")?.toIntOrNull(),
                masterPageName = masterName
            )
        }
    }

    /** Inserts in-flow section markers when paragraph styles switch master pages. */
    fun bindBlocks(blocks: List<BlockNode>, sections: List<SectionStyle>): List<BlockNode> {
        val byName = sections.mapNotNull { section ->
            section.masterPageName?.let { it to section }
        }.toMap()
        val result = mutableListOf<BlockNode>()
        var activeMaster: String? = null
        for (block in blocks) {
            if (block is BlockNode.SectionBreak) {
                result += block
                activeMaster = block.style.masterPageName
                continue
            }
            val masterName = when (block) {
                is BlockNode.Paragraph -> block.style.masterPageName
                is BlockNode.Heading -> block.style.masterPageName
                else -> null
            }
            if (masterName != null && masterName != activeMaster) {
                val section = byName[masterName]
                if (section != null) {
                    result += BlockNode.SectionBreak(section)
                    activeMaster = masterName
                }
            }
            result += block
        }
        return result
    }

    fun collectHeaderFooters(stylesRoot: Element?): List<HeaderFooterStory> {
        if (stylesRoot == null) return emptyList()
        val stories = mutableListOf<HeaderFooterStory>()
        stylesRoot.descendantsNs(Ns.STYLE, "master-page").forEach { master ->
            val children = master.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child !is Element) continue
                when {
                    child.matchesNs(Ns.STYLE, "header") -> stories += story(child, HeaderFooterKind.HEADER, HeaderFooterVariant.DEFAULT)
                    child.matchesNs(Ns.STYLE, "footer") -> stories += story(child, HeaderFooterKind.FOOTER, HeaderFooterVariant.DEFAULT)
                    child.matchesNs(Ns.STYLE, "header-first") -> stories += story(child, HeaderFooterKind.HEADER, HeaderFooterVariant.FIRST)
                    child.matchesNs(Ns.STYLE, "footer-first") -> stories += story(child, HeaderFooterKind.FOOTER, HeaderFooterVariant.FIRST)
                    child.matchesNs(Ns.STYLE, "header-left") -> stories += story(child, HeaderFooterKind.HEADER, HeaderFooterVariant.EVEN)
                    child.matchesNs(Ns.STYLE, "footer-left") -> stories += story(child, HeaderFooterKind.FOOTER, HeaderFooterVariant.EVEN)
                }
            }
        }
        return stories
    }

    private fun story(element: Element, kind: HeaderFooterKind, variant: HeaderFooterVariant): HeaderFooterStory =
        HeaderFooterStory(
            kind = kind,
            variant = variant,
            blocks = blockMapper.parseBlocks(element),
            anchor = SourceAnchor(context.part, "style:${element.localName}")
        )
}
