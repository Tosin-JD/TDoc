package com.tosin.docprocessor.domain.model.layout

import android.graphics.RectF
import com.tosin.docprocessor.domain.model.DocumentElement

/**
 * Geometric bridge between raw content and physical location.
 * All bounds are in Points (pt).
 */
data class PositionedElement(
    val elementId: String,       // Reference to the DocumentElement ID
    val element: DocumentElement, // The actual content snapshot
    val pageIndex: Int,          // Which page it belongs to
    val bounds: RectF,           // X, Y, Width, Height in Points relative to Page (0,0)
    val indentation: ElementIndentation = ElementIndentation(),
    val metadata: Map<String, Any> = emptyMap(), // For things like "Split across pages" (lineStart, lineEnd)
    val layoutResult: LayoutResult = LayoutResult.Empty
)

/**
 * Resolved indentation values in Points (pt).
 */
data class ElementIndentation(
    val leftPt: Float = 0f,
    val rightPt: Float = 0f,
    val firstLinePt: Float = 0f,
    val hangingPt: Float = 0f
)
