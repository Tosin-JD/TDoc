package com.tosin.docprocessor.data.common.model.layout

import com.tosin.docprocessor.data.common.model.DocumentElement

data class PositionedElement(
    val element: DocumentElement,
    val x: Float, // Physical point relative to page top-left
    val y: Float, // Physical point relative to page top-left
    val width: Float,
    val height: Float
)
