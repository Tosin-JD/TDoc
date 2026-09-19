package com.tosin.docprocessor.domain.editor

import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.model.EditorBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoManagerTest {

    private val meta = DocumentMeta(uri = "content://doc", fileName = "doc.txt", mimeType = "text/plain")

    private fun doc(text: String, blockId: Long = 1L) = EditorDocument(
        meta = meta,
        blocks = listOf(EditorBlock(id = blockId, spans = listOf(com.tosin.docprocessor.data.parser.internal.models.TextSpan(text = text)))),
        nextBlockId = 2L
    )

    @Test
    fun `empty history has no undo or redo`() {
        val manager = UndoManager()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertNull(manager.undo(doc("a")))
        assertNull(manager.redo(doc("a")))
    }

    @Test
    fun `push then undo restores previous state`() {
        val manager = UndoManager()
        val previous = doc("previous")
        val current = doc("current")
        manager.push(previous)
        assertTrue(manager.canUndo)
        assertEquals(previous, manager.undo(current))
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)
    }

    @Test
    fun `undo then redo restores state and re-enables undo`() {
        val manager = UndoManager()
        val previous = doc("previous")
        val current = doc("current")
        manager.push(previous)
        val undone = manager.undo(current)!!
        assertEquals(previous, undone)
        val redone = manager.redo(undone)!!
        assertEquals(current, redone)
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
    }

    @Test
    fun `push clears redo history`() {
        val manager = UndoManager()
        manager.push(doc("a"))
        manager.undo(doc("b"))
        assertTrue(manager.canRedo)
        manager.push(doc("c"))
        assertFalse(manager.canRedo)
    }

    @Test
    fun `multiple levels unwind in LIFO order`() {
        val manager = UndoManager()
        val a = doc("a")
        val b = doc("b")
        val c = doc("c")
        val d = doc("d")
        manager.push(a)
        manager.push(b)
        manager.push(c)
        assertEquals(c, manager.undo(d))
        assertEquals(b, manager.undo(c))
        assertEquals(a, manager.undo(b))
        assertFalse(manager.canUndo)
    }

    @Test
    fun `capacity bounds the undo stack`() {
        val manager = UndoManager(capacity = 3)
        val snapshots = (0 until 10).map { doc("s$it", blockId = it.toLong()) }
        snapshots.forEach { manager.push(it) }
        // Only the last 3 snapshots survive.
        var current = doc("after")
        val seen = mutableListOf<EditorDocument>()
        while (manager.canUndo) {
            current = manager.undo(current)!!
            seen += current
        }
        assertEquals(listOf(snapshots[9], snapshots[8], snapshots[7]), seen)
    }

    @Test
    fun `clear resets both stacks`() {
        val manager = UndoManager()
        manager.push(doc("a"))
        manager.undo(doc("b"))
        assertTrue(manager.canUndo || manager.canRedo)
        manager.clear()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }
}