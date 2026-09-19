package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.EditorBlock

/**
 * Immutable document model. All edits return a new instance; only the changed
 * block is replaced so the shared list cheaply provides structural sharing.
 */
data class EditorDocument(
    val meta: DocumentMeta,
    val blocks: List<EditorBlock>,
    val nextBlockId: Long
) {

    fun indexOf(id: Long): Int = blocks.indexOfFirst { it.id == id }

    fun blockAt(id: Long): EditorBlock? = blocks.firstOrNull { it.id == id }

    /** Splits allocation of a fresh id from the next mutation. */
    fun allocateBlockId(): Pair<Long, EditorDocument> = nextBlockId to copy(nextBlockId = nextBlockId + 1)

    fun replaceBlock(block: EditorBlock): EditorDocument {
        val index = indexOf(block.id)
        if (index < 0) return this
        val updated = blocks.toMutableList()
        updated[index] = block
        return copy(blocks = updated)
    }

    fun insertAfter(afterId: Long, block: EditorBlock): EditorDocument {
        val index = indexOf(afterId)
        if (index < 0) return copy(blocks = blocks + block)
        val updated = blocks.toMutableList()
        updated.add(index + 1, block)
        return copy(blocks = updated)
    }

    fun append(block: EditorBlock): EditorDocument = copy(blocks = blocks + block)

    fun removeBlock(id: Long): EditorDocument {
        val index = indexOf(id)
        if (index < 0) return this
        val updated = blocks.toMutableList().apply { removeAt(index) }
        return copy(blocks = updated)
    }
}