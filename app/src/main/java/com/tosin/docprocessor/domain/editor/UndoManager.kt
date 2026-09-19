package com.tosin.docprocessor.domain.editor

/**
 * Bounded undo/redo history. The latest snapshot sits in the editor's current
 * state; [push] stores the state that existed *before* an edit.
 */
class UndoManager(private val capacity: Int = MAX_CAPACITY) {

    private val undoStack = ArrayDeque<EditorDocument>()
    private val redoStack = ArrayDeque<EditorDocument>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(snapshot: EditorDocument) {
        while (undoStack.size >= capacity) undoStack.removeFirst()
        undoStack.addLast(snapshot)
        redoStack.clear()
    }

    /** Returns the state to restore when undoing from [current]. */
    fun undo(current: EditorDocument): EditorDocument? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return previous
    }

    /** Returns the state to restore when redoing from [current]. */
    fun redo(current: EditorDocument): EditorDocument? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private companion object {
        const val MAX_CAPACITY = 50
    }
}