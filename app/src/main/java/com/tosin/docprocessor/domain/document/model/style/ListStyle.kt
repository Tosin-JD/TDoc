package com.tosin.docprocessor.domain.document.model.style

enum class ListKind { ORDERED, UNORDERED, MIXED }

data class ListLevel(
    val level: Int,
    val format: String? = null,
    val levelText: String? = null,
    val start: Int? = null,
    val indentPoints: Double? = null,
    val bulletFont: String? = null,
    val bulletChar: String? = null,
    val isLegal: Boolean = false
)

data class ListStyle(
    val styleId: String? = null,
    val styleName: String? = null,
    val levels: Map<Int, ListLevel> = emptyMap(),
    val kind: ListKind = ListKind.UNORDERED
)
