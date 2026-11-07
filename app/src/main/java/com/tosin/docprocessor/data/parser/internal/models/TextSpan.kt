package com.tosin.docprocessor.data.parser.internal.models

import com.tosin.docprocessor.domain.document.model.style.Shading

data class TextSpan(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val isHidden: Boolean = false,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    /**
     * Authored run colour (hex `#RRGGBB`/`RRGGBB`, `rgb()`, or a known colour
     * name). `null` means automatic/inherited — renderers substitute the
     * theme-aware document text colour, so auto text follows light/dark mode
     * while authored colours are preserved and only contrast-corrected.
     */
    val color: String? = null,
    val highlightColor: String? = null,
    /** Run-level shading (`w:shd`/`fo:background-color` on the run). */
    val shading: Shading? = null,
    val characterSpacing: Int? = null,
    val language: String? = null,
    val hasShadow: Boolean = false,
    val hasOutline: Boolean = false,
    val isEmbossed: Boolean = false,
    val isEngraved: Boolean = false,
    val isSmallCaps: Boolean = false,
    val isAllCaps: Boolean = false,
    /** Link data is kept on the exact inline range, never inferred from colour. */
    val hyperlink: HyperlinkInfo? = null
) {
    constructor(
        text: String?,
        isBold: Boolean?,
        isItalic: Boolean?,
        color: String?
    ) : this(
        text = text ?: "",
        isBold = isBold ?: false,
        isItalic = isItalic ?: false,
        color = color
    )
}
