package com.tosin.docprocessor.data.common.model.layout

data class PageDimensions(
    val width: Float,  // in points
    val height: Float, // in points
    val marginLeft: Float = 72f,
    val marginTop: Float = 72f,
    val marginRight: Float = 72f,
    val marginBottom: Float = 72f
) {
    companion object {
        val A4 = PageDimensions(595.27f, 841.89f)
        val Letter = PageDimensions(612f, 792f)
    }

    val contentWidth: Float
        get() = width - marginLeft - marginRight

    val contentHeight: Float
        get() = height - marginTop - marginBottom
}
