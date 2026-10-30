package com.tosin.docprocessor.domain.model

import android.graphics.RectF
import android.text.StaticLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tosin.docprocessor.domain.model.layout.ElementIndentation

/**
 * Pre-measured, render-ready element.
 *
 * Bridges the immutable [DocumentBlock] and the physical page layout.
 * All layout is computed once during pagination (on a background thread)
 * and cached. The canvas renderer reads this model directly — no re-layout
 * happens during rendering.
 *
 * The pre-measured [layout] is an [android.text.StaticLayout] produced by
 * [com.tosin.docprocessor.domain.pagination.TextMeasurer], already placed
 * in physical pixel coordinates by the paginator. It is drawn directly
 * onto the canvas — no per-line Compose [androidx.compose.ui.text.Text]
 * components are instantiated during rendering.
 */
data class RenderableElement(
    val element: DocumentBlock,
    val pageIndex: Int,
    val bounds: RectF,
    val indentation: ElementIndentation = ElementIndentation(),
    val layout: StaticLayout? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    val elementId: String get() = element.id
    val height: Float get() = layout?.height?.toFloat() ?: 0f

    /**
     * Draws this element onto the canvas.
     *
     * The canvas is already configured with the page's transform matrix.
     * This method paints the text layout, table borders, and any decorations.
     */
    fun draw(
        drawScope: DrawScope,
        backgroundColor: ComposeColor,
        selectionState: SelectionState? = null,
        isDarkMode: Boolean = false
    ) {
        // Draw selection background
        if (selectionState?.intersects(elementId) == true) {
            drawScope.drawRect(
                color = ComposeColor.Blue.copy(alpha = 0.2f),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width(), bounds.height())
            )
        }

        // Draw the pre-measured text layout
        layout?.let { currentLayout ->
            drawScope.drawContext.canvas.nativeCanvas.apply {
                save()
                translate(bounds.left, bounds.top)
                currentLayout.draw(this)
                restore()
            }
        }

        // Draw selection cursor (caret)
        if (selectionState?.isCursorAt(elementId) == true) {
            val cursorHeight = height.coerceAtLeast(1f)
            drawScope.drawRect(
                color = ComposeColor.Black,
                topLeft = Offset(
                    bounds.left + 2f,
                    bounds.top + (bounds.height() - 2f) / 2f
                ),
                size = Size(2f, cursorHeight)
            )
        }
    }
}

/**
 * State for text selection during editing.
 *
 * Tracks which elements are selected and where the cursor is positioned.
 */
data class SelectionState(
    val selectedElementIds: Set<String> = emptySet(),
    val cursorElementId: String? = null,
    val cursorOffset: Int = 0  // Character offset within the element
) {
    fun intersects(elementId: String): Boolean = selectedElementIds.contains(elementId) || cursorElementId == elementId
    fun isCursorAt(elementId: String): Boolean = cursorElementId == elementId
    fun isEmpty(): Boolean = selectedElementIds.isEmpty() && cursorElementId == null
}
