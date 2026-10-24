package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.model.BlockNode

/**
 * Attaches caption-styled paragraphs that directly follow a [BlockNode.Figure]
 * to that figure's `caption` slot instead of leaving them in the flow. Pure
 * and JVM-testable. DOCX images are inline (no block figure), so their
 * "Caption"-styled paragraphs stay in flow — documented behavior.
 */
object CaptionAttacher {

    private val CAPTION_STYLE_IDS = setOf("caption", "illustration", "figure", "tablecaption")

    fun attach(blocks: List<BlockNode>): List<BlockNode> {
        val output = mutableListOf<BlockNode>()
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            val next = blocks.getOrNull(index + 1)
            if (block is BlockNode.Figure && next != null && next is BlockNode.Paragraph && isCaptionParagraph(next)) {
                output += block.copy(caption = next.inlines)
                index += 2
                continue
            }
            output += block
            index++
        }
        return output
    }

    private fun isCaptionParagraph(block: BlockNode): Boolean {
        val paragraph = block as? BlockNode.Paragraph ?: return false
        val styleId = paragraph.style.styleId?.lowercase()
        val styleName = paragraph.style.styleName?.lowercase()
        return styleId in CAPTION_STYLE_IDS || styleName in CAPTION_STYLE_IDS
    }
}
