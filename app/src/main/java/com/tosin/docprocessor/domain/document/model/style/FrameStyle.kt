package com.tosin.docprocessor.domain.document.model.style

enum class FrameAnchor { INLINE, ANCHORED }

data class FrameStyle(
    val anchor: FrameAnchor = FrameAnchor.INLINE,
    val widthPoints: Double? = null,
    val heightPoints: Double? = null,
    val positionXPoints: Double? = null,
    val positionYPoints: Double? = null,
    val rotationDegrees: Double? = null,
    val wrapStyle: String? = null,
    val zOrder: Int? = null,
    /** Authored shape fill colour (hex/`rgb()`/named) when declared. */
    val fillColor: String? = null,
    /** Authored shape outline colour when declared. */
    val strokeColor: String? = null
)
