package com.tosin.docprocessor.data.common.model.layout

import android.graphics.RectF
import com.tosin.docprocessor.data.common.model.DocumentElement

/**
 * Geometric bridge between raw content and physical location.
 * All bounds are in Points (pt).
 */
data class PositionedElement(
    val elementId: String,       // Reference to the DocumentElement ID
    val element: DocumentElement, // The actual content snapshot
    val pageIndex: Int,          // Which page it belongs to
    val bounds: RectF,           // X, Y, Width, Height in Points relative to Page (0,0)
    val metadata: Map<String, Any> = emptyMap() // For things like "Split across pages" (lineStart, lineEnd)
)
