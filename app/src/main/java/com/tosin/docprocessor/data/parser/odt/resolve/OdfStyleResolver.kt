package com.tosin.docprocessor.data.parser.odt.resolve

import com.tosin.docprocessor.data.parser.common.Ns
import com.tosin.docprocessor.data.parser.common.attrNs
import com.tosin.docprocessor.data.parser.common.elementsNs
import com.tosin.docprocessor.data.parser.common.firstNs
import com.tosin.docprocessor.domain.document.model.StyleCatalogue
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
    private val listPool: Map<String, Element>,
    private val tablePool: Map<String, Element>,
    private val cellPool: Map<String, Element>,
    private val pageLayouts: Map<String, PageStyle>,
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

    fun pageStyle(name: String?): PageStyle = name?.let(pageLayouts::get) ?: PageStyle()

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
            val listPool = linkedMapOf<String, Element>()
            val tablePool = linkedMapOf<String, Element>()
            val cellPool = linkedMapOf<String, Element>()
            val pageLayouts = linkedMapOf<String, PageStyle>()
            var defaultParagraph = ParagraphStyle()
            var defaultText = TextStyle()

            roots.forEach { root ->
                collectStyles(root, "style", textPool, paragraphPool, tablePool, cellPool)
                collectListStyles(root, listPool)
                collectPageLayouts(root, pageLayouts)
                root.elementsNs(Ns.STYLE, "default-style").forEach { style ->
                    when (style.attrNs(Ns.STYLE, "family")) {
                        "paragraph" -> defaultParagraph = OdfParagraphProperties.parse(style)
                        "text" -> defaultText = OdfTextProperties.parse(style)
                    }
                }
            }
            return OdfStyleResolver(
                textPool, paragraphPool, listPool, tablePool, cellPool, pageLayouts, defaultParagraph, defaultText
            )
        }

        private fun collectStyles(
            root: Element,
            local: String,
            textPool: MutableMap<String, Element>,
            paragraphPool: MutableMap<String, Element>,
            tablePool: MutableMap<String, Element>,
            cellPool: MutableMap<String, Element>
        ) {
            root.elementsNs(Ns.STYLE, local).forEach { style ->
                val name = style.attrNs(Ns.STYLE, "name") ?: return@forEach
                when (style.attrNs(Ns.STYLE, "family")) {
                    "text" -> textPool[name] = style
                    "paragraph" -> paragraphPool[name] = style
                    "table" -> tablePool[name] = style
                    "table-cell" -> cellPool[name] = style
                }
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
