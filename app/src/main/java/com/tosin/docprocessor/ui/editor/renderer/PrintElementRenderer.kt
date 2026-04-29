package com.tosin.docprocessor.ui.editor.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PositionedElement

class PrintElementRenderer {

    fun render(canvas: Canvas, positionedElement: PositionedElement, scale: Float) {
        val element = positionedElement.element
        val bounds = positionedElement.bounds
        val x = bounds.left * scale
        val y = bounds.top * scale
        val width = bounds.width() * scale
        val height = bounds.height() * scale

        when (element) {
            is DocumentElement.Paragraph -> {
                val text = element.spans.joinToString("") { it.text }
                val textPaint = TextPaint().apply {
                    color = Color.BLACK
                    textSize = 12f * scale // Simplified
                }

                val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()

                canvas.save()
                canvas.translate(x, y)
                layout.draw(canvas)
                canvas.restore()
            }
            is DocumentElement.Image -> {
                val paint = Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                }
                canvas.drawRect(x, y, x + width, y + height, paint)
                // Draw image logic here
            }
            // Add other renderers
            else -> {
                val paint = Paint().apply {
                    color = Color.MAGENTA
                    alpha = 50
                }
                canvas.drawRect(x, y, x + width, y + height, paint)
            }
        }
    }
}
