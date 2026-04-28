package com.tosin.docprocessor.ui.editor.engine

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tosin.docprocessor.data.parser.internal.models.ParagraphStyle
import com.tosin.docprocessor.data.parser.internal.models.TextSpan

class TextMeasurer {
    
    fun measureParagraph(
        spans: List<TextSpan>,
        style: ParagraphStyle,
        maxWidthPx: Int
    ): Int {
        // This is a simplified version. A real implementation would convert TextSpans 
        // to SpannableString with proper Typefaces, sizes, etc.
        val text = spans.joinToString("") { it.text }
        if (text.isEmpty()) return 0
        
        val paint = TextPaint().apply {
            textSize = 12f * 1.33f // Default 12pt converted to px (roughly)
            // In a real app, we'd iterate spans and apply styles
        }

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
            
        return layout.height
    }

    // Helper to convert points to pixels for measurement
    // Usually points are 1/72 inch, and we might want to scale based on screen density
    // For WYSIWYG, we often use a fixed PPI (e.g. 96 or 72)
    fun pointsToPx(points: Float): Int {
        return (points * 1.33f).toInt() // 96/72 = 1.33
    }
}
