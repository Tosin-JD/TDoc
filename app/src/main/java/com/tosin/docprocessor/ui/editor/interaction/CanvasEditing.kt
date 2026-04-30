package com.tosin.docprocessor.ui.editor.interaction

import android.graphics.RectF
import android.text.StaticLayout
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PositionedElement
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel

enum class EditableElementKind {
    PARAGRAPH,
    SECTION_HEADER
}

data class CanvasTextHitTarget(
    val elementId: String,
    val kind: EditableElementKind,
    val pageIndex: Int,
    val text: String,
    val charIndex: Int
)

@Composable
fun CanvasEditOverlay(
    pageElements: List<PositionedElement>,
    pageScale: Float,
    unitConverter: UnitConverter,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val activeTarget = viewModel.activeCanvasEditorTarget
    val caretRect = remember(activeTarget, pageElements) {
        activeTarget?.let { resolveCaretRect(it, pageElements, unitConverter) }
    }
    val transition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(pageElements, pageScale, viewModel.documentElements) {
                detectTapGestures { offset ->
                    val hit = resolveHitTarget(
                        pageElements = pageElements,
                        tapOffsetPx = offset,
                        pageScale = pageScale,
                        unitConverter = unitConverter,
                        sourceTextForId = viewModel::getEditableTextForElement
                    )
                    if (hit != null) {
                        viewModel.beginCanvasTextEdit(hit)
                    } else {
                        viewModel.clearActiveCanvasTextEdit()
                    }
                }
            }
    ) {
        val caret = caretRect ?: return@Canvas
        if (activeTarget?.pageIndex != pageElements.firstOrNull()?.pageIndex) return@Canvas

        drawRect(
            color = Color(0xFF2563EB).copy(alpha = caretAlpha),
            topLeft = Offset(
                x = unitConverter.ptToPx(caret.left) * pageScale,
                y = unitConverter.ptToPx(caret.top) * pageScale
            ),
            size = androidx.compose.ui.geometry.Size(
                width = 2.dp.toPx(),
                height = unitConverter.ptToPx(caret.height()) * pageScale
            )
        )
    }
}

fun resolveHitTarget(
    pageElements: List<PositionedElement>,
    tapOffsetPx: Offset,
    pageScale: Float,
    unitConverter: UnitConverter,
    sourceTextForId: (String) -> String?
): CanvasTextHitTarget? {
    val scale = pageScale.takeIf { it > 0f } ?: 1f
    val pageXPt = unitConverter.pxToPt(tapOffsetPx.x / scale)
    val pageYPt = unitConverter.pxToPt(tapOffsetPx.y / scale)
    val positionedElement = pageElements
        .lastOrNull { it.bounds.contains(pageXPt, pageYPt) && it.element.toEditableKind() != null }
        ?: return null

    val text = sourceTextForId(positionedElement.elementId) ?: return null
    val kind = positionedElement.element.toEditableKind() ?: return null
    val layout = positionedElement.layoutResult as? StaticLayout
    val charIndex = if (layout != null) {
        val localXpx = unitConverter.ptToPx(pageXPt - positionedElement.bounds.left)
        val localYpx = unitConverter.ptToPx(pageYPt - positionedElement.bounds.top)
        val line = layout.getLineForVertical(localYpx.toInt().coerceAtLeast(0))
        val localOffset = layout.getOffsetForHorizontal(line, localXpx)
        val fragmentText = layout.text?.toString().orEmpty()
        val fragmentStart = findFragmentStart(text, fragmentText)
        (fragmentStart + localOffset).coerceIn(0, text.length)
    } else {
        text.length
    }

    return CanvasTextHitTarget(
        elementId = positionedElement.elementId,
        kind = kind,
        pageIndex = positionedElement.pageIndex,
        text = text,
        charIndex = charIndex
    )
}

fun resolveCaretRect(
    activeTarget: EditorViewModel.ActiveCanvasEditorTarget,
    pageElements: List<PositionedElement>,
    unitConverter: UnitConverter
): RectF? {
    val positionedElement = pageElements.firstOrNull { it.elementId == activeTarget.elementId } ?: return null
    val layout = positionedElement.layoutResult as? StaticLayout ?: return null
    val fragmentText = layout.text?.toString().orEmpty()
    val fragmentStart = findFragmentStart(activeTarget.text, fragmentText)
    val fragmentEnd = (fragmentStart + fragmentText.length).coerceAtMost(activeTarget.text.length)
    val globalOffset = activeTarget.selection.end.coerceIn(0, activeTarget.text.length)

    if (globalOffset < fragmentStart || globalOffset > fragmentEnd) return null

    val localOffset = (globalOffset - fragmentStart).coerceIn(0, fragmentText.length)
    val line = layout.getLineForOffset(localOffset)
    val xPt = positionedElement.bounds.left + unitConverter.pxToPt(layout.getPrimaryHorizontal(localOffset))
    val topPt = positionedElement.bounds.top + unitConverter.pxToPt(layout.getLineTop(line).toFloat())
    val bottomPt = positionedElement.bounds.top + unitConverter.pxToPt(layout.getLineBottom(line).toFloat())
    return RectF(xPt, topPt, xPt + 1.5f, bottomPt)
}

private fun DocumentElement.toEditableKind(): EditableElementKind? =
    when (this) {
        is DocumentElement.Paragraph -> EditableElementKind.PARAGRAPH
        is DocumentElement.SectionHeader -> EditableElementKind.SECTION_HEADER
        else -> null
    }

private fun findFragmentStart(fullText: String, fragmentText: String): Int {
    if (fragmentText.isBlank()) return 0
    return fullText.indexOf(fragmentText).takeIf { it >= 0 } ?: 0
}
