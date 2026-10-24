package com.tosin.docprocessor.data.parser.odt.resolve

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.descendantsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.StyleCatalogue
import com.tosin.docprocessor.domain.document.model.Units
import com.tosin.docprocessor.domain.document.model.style.CellStyle
import com.tosin.docprocessor.domain.document.model.style.ListStyle
import com.tosin.docprocessor.domain.document.model.style.PageStyle
import com.tosin.docprocessor.domain.document.model.style.ParagraphStyle
import com.tosin.docprocessor.domain.document.model.style.TableStyle
import com.tosin.docprocessor.domain.document.model.style.TextStyle
import org.w3c.dom.Element

/**
 * Resolves ODF styles from `styles.xml` and the automatic styles in
 * `content.xml`. Automatic styles win when names collide. Immutable per import.
 */
class OdfStyleResolver private constructor(
    private val textPool: Map<String, Element>,
    private val paragraphPool: Map<String, Element>,
    private val sectionMasterPages: Map<String, String>,
    private val listPool: Map<String, Element>,
    private val tablePool: Map<String, Element>,
    private val cellPool: Map<String, Element>,
    private val columnPool: Map<String, Element>,
    private val rowPool: Map<String, Element>,
    private val pageLayouts: Map<String, PageStyle>,
    private val masterPageLayouts: Map<String, String>,
    private val defaultParagraphStyle: ParagraphStyle,
    private val defaultTextStyle: TextStyle
) {

    private val textCache = HashMap<String, TextStyle>()
    private val paragraphCache = HashMap<String, ParagraphStyle>()
    private val listCache = HashMap<String, ListStyle>()
    private val tableCache = HashMap<String, TableStyle>()
    private val cellCache = HashMap<String, CellStyle>()

    fun textStyle(name: String?): TextStyle {
        if (name == null) return defaultTextStyle
        return textCache.getOrPut(name) {
            if (!textPool.containsKey(name)) return@getOrPut defaultTextStyle
            var result = defaultTextStyle
            chain(name, textPool).forEach { result = result.mergedWith(OdfTextProperties.parse(it)) }
            result
        }
    }

    fun paragraphStyle(name: String?): ParagraphStyle {
        if (name == null) return defaultParagraphStyle
        return paragraphCache.getOrPut(name) {
            if (!paragraphPool.containsKey(name)) return@getOrPut defaultParagraphStyle
            var result = defaultParagraphStyle
            chain(name, paragraphPool).forEach { result = result.mergedWith(OdfParagraphProperties.parse(it)) }
            result.copy(styleId = name, styleName = name)
        }
    }

    fun listStyle(name: String?): ListStyle {
        if (name == null) return ListStyle()
        return listCache.getOrPut(name) {
            listPool[name]?.let { OdfListProperties.parse(it) } ?: ListStyle()
        }
    }

    fun tableStyle(name: String?): TableStyle {
        if (name == null) return TableStyle()
        return tableCache.getOrPut(name) {
            tablePool[name]?.let { OdfTableProperties.parseTable(it) } ?: TableStyle()
        }
    }

    fun cellStyle(name: String?): CellStyle {
        if (name == null) return CellStyle()
        return cellCache.getOrPut(name) {
            cellPool[name]?.let { OdfTableProperties.parseCell(it) } ?: CellStyle()
        }
    }

    fun hasRelativeCellWidth(name: String?): Boolean {
        val properties = name?.let(cellPool::get)?.firstNs(Ns.STYLE, "table-cell-properties")
        return properties?.attrNs(Ns.STYLE, "width")?.contains('%') == true
    }

    fun pageStyle(name: String?): PageStyle = name?.let(pageLayouts::get) ?: PageStyle()

    /** Resolves a `style:master-page` name to its page layout geometry. */
    fun pageStyleForMasterPage(name: String?): PageStyle? =
        name?.let(masterPageLayouts::get)?.let(pageLayouts::get)

    fun masterPageForSectionStyle(name: String?): String? = name?.let(sectionMasterPages::get)

    /** Column width in points from `style:column-width` (null when optimal/relative). */
    fun columnWidth(name: String?): Double? = name?.let(columnPool::get)?.let {
        OdfTableProperties.parseColumnWidth(it)
    }

    /** Row height in points from `style:row-height` (minimum height; null when optimal). */
    fun rowHeight(name: String?): Double? {
        val element = name?.let(rowPool::get) ?: return null
        val props = element.firstNs(Ns.STYLE, "row-properties") ?: return null
        return Units.parseOdfLength(props.attrNs(Ns.STYLE, "row-height"))
    }

    fun catalogue(): StyleCatalogue = StyleCatalogue(
        paragraphStyles = paragraphPool.keys.associateWith { paragraphStyle(it) },
        textStyles = textPool.keys.associateWith { textStyle(it) },
        listStyles = listPool.keys.associateWith { listStyle(it) },
        tableStyles = tablePool.keys.associateWith { tableStyle(it) },
        defaultParagraphStyle = defaultParagraphStyle,
        defaultTextStyle = defaultTextStyle
    )

    private fun chain(name: String, pool: Map<String, Element>): List<Element> {
        val chain = mutableListOf<Element>()
        val visited = mutableSetOf<String>()
        var current: Element? = pool[name]
        while (current != null) {
            val id = current.attrNs(Ns.STYLE, "name") ?: break
            if (!visited.add(id)) break
            chain += current
            current = current.attrNs(Ns.STYLE, "parent-style-name")?.let(pool::get)
        }
        return chain.reversed()
    }

    companion object {
        fun parse(styleRoots: List<Element>, contentRoot: Element?): OdfStyleResolver {
            val roots = styleRoots.toMutableList()
            if (contentRoot != null) roots += contentRoot

            val textPool = linkedMapOf<String, Element>()
            val paragraphPool = linkedMapOf<String, Element>()
            val sectionMasterPages = linkedMapOf<String, String>()
            val listPool = linkedMapOf<String, Element>()
            val tablePool = linkedMapOf<String, Element>()
            val cellPool = linkedMapOf<String, Element>()
            val columnPool = linkedMapOf<String, Element>()
            val rowPool = linkedMapOf<String, Element>()
            val pageLayouts = linkedMapOf<String, PageStyle>()
            val masterPageLayouts = linkedMapOf<String, String>()
            var defaultParagraph = ParagraphStyle()
            var defaultText = TextStyle()

            // Styles may sit directly under the root or under the
            // office:styles / office:automatic-styles wrapper elements.
            fun containers(root: Element): List<Element> =
                listOf(root) + root.elementsNs(Ns.OFFICE, "styles") +
                    root.elementsNs(Ns.OFFICE, "automatic-styles") +
                    root.elementsNs(Ns.OFFICE, "master-styles")

            roots.forEach { root ->
                containers(root).forEach { container ->
                    collectStyles(
                        container,
                        "style",
                        textPool,
                        paragraphPool,
                        sectionMasterPages,
                        tablePool,
                        cellPool
                    )
                    collectMemberStyles(container, "table-column", columnPool)
                    collectMemberStyles(container, "table-row", rowPool)
                collectListStyles(container, listPool)
                collectPageLayouts(container, pageLayouts)
                container.descendantsNs(Ns.STYLE, "master-page").forEach { master ->
                    master.attrNs(Ns.STYLE, "name")?.let { name ->
                        master.attrNs(Ns.STYLE, "page-layout-name")?.let { layout ->
                            masterPageLayouts[name] = layout
                        }
                    }
                }
                    container.elementsNs(Ns.STYLE, "default-style").forEach { style ->
                        when (style.attrNs(Ns.STYLE, "family")) {
                            "paragraph" -> defaultParagraph = OdfParagraphProperties.parse(style)
                            "text" -> defaultText = OdfTextProperties.parse(style)
                        }
                    }
                }
            }
            return OdfStyleResolver(
                textPool, paragraphPool, sectionMasterPages, listPool, tablePool, cellPool,
                columnPool, rowPool, pageLayouts, masterPageLayouts, defaultParagraph, defaultText
            )
        }

        private fun collectStyles(
            root: Element,
            local: String,
            textPool: MutableMap<String, Element>,
            paragraphPool: MutableMap<String, Element>,
            sectionMasterPages: MutableMap<String, String>,
            tablePool: MutableMap<String, Element>,
            cellPool: MutableMap<String, Element>
        ) {
            root.elementsNs(Ns.STYLE, local).forEach { style ->
                val name = style.attrNs(Ns.STYLE, "name") ?: return@forEach
                when (style.attrNs(Ns.STYLE, "family")) {
                    "text" -> textPool[name] = style
                    "paragraph" -> paragraphPool[name] = style
                    "section" -> style.attrNs(Ns.STYLE, "master-page-name")
                        ?.let { sectionMasterPages[name] = it }
                    "table" -> tablePool[name] = style
                    "table-cell" -> cellPool[name] = style
                }
            }
        }

        private fun collectMemberStyles(root: Element, family: String, pool: MutableMap<String, Element>) {
            root.elementsNs(Ns.STYLE, "style").forEach { style ->
                if (style.attrNs(Ns.STYLE, "family") != family) return@forEach
                style.attrNs(Ns.STYLE, "name")?.let { pool[it] = style }
            }
        }

        private fun collectListStyles(root: Element, pool: MutableMap<String, Element>) {
            root.elementsNs(Ns.TEXT, "list-style").forEach { style ->
                style.attrNs(Ns.STYLE, "name")?.let { pool[it] = style }
            }
        }

        private fun collectPageLayouts(root: Element, pool: MutableMap<String, PageStyle>) {
            root.elementsNs(Ns.STYLE, "page-layout").forEach { layout ->
                layout.attrNs(Ns.STYLE, "name")?.let { pool[it] = OdfPageProperties.parse(layout) }
            }
        }
    }
}
