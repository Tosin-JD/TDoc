package com.tosin.docprocessor.data.parser.internal.models

data class HyperlinkInfo(
    val address: String? = null,
    val anchor: String? = null,
    val tooltip: String? = null,
    val docLocation: String? = null
) {
    val isExternal: Boolean
        get() = !address.isNullOrBlank()

    val isEmail: Boolean
        get() = address?.startsWith("mailto:", ignoreCase = true) == true
}

data class ListInfo(
    val level: Int = 0,
    val format: String? = null,
    val levelText: String? = null,
    val startOverride: Int? = null,
    val bulletFont: String? = null
)

data class EdgeInsets(
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null
)

data class SectionProperties(
    val sectionIndex: Int,
    val source: String,
    val type: String? = null,
    val pageWidth: Int? = null,
    val pageHeight: Int? = null,
    val margins: EdgeInsets = EdgeInsets(),
    val columnCount: Int? = null,
    val pageNumberStart: Int? = null,
    val headerReferences: List<HeaderFooterReference> = emptyList(),
    val footerReferences: List<HeaderFooterReference> = emptyList()
)

data class HeaderFooterReference(
    val relationshipId: String? = null,
    val type: String? = null
)

data class HeaderFooterContent(
    val kind: HeaderFooterKind,
    val variant: String,
    val text: String,
    val paragraphCount: Int,
    val tableCount: Int
)

enum class HeaderFooterKind {
    HEADER,
    FOOTER
}

data class NoteInfo(
    val kind: NoteKind,
    val id: String,
    val text: String
)

enum class NoteKind {
    FOOTNOTE,
    ENDNOTE
}

data class CommentInfo(
    val id: String,
    val author: String? = null,
    val initials: String? = null,
    val date: String? = null,
    val text: String
)

data class BookmarkInfo(
    val id: String,
    val name: String
)

data class FieldInfo(
    val type: String,
    val instruction: String,
    val value: String? = null,
    val isSimpleField: Boolean = false
)

data class DrawingInfo(
    val kind: String,
    val isInline: Boolean,
    val widthEmu: Long? = null,
    val heightEmu: Long? = null,
    val altText: String? = null,
    val title: String? = null,
    val positionX: String? = null,
    val positionY: String? = null,
    val wrapStyle: String? = null,
    val hasChart: Boolean = false,
    val hasSmartArt: Boolean = false
)

data class EmbeddedObjectInfo(
    val kind: String,
    val programId: String? = null,
    val shapeId: String? = null,
    val description: String? = null
)

data class TableMetadata(
    val styleId: String? = null,
    val caption: String? = null,
    val description: String? = null,
    val shadingColor: String? = null,
    val borderSummary: String? = null,
    val cellMargins: EdgeInsets = EdgeInsets(),
    val rows: List<List<TableCellMetadata>> = emptyList()
)

data class TableCellMetadata(
    val gridSpan: Int? = null,
    val horizontalMerge: String? = null,
    val verticalMerge: String? = null,
    val shadingColor: String? = null,
    val borderSummary: String? = null,
    val margins: EdgeInsets = EdgeInsets(),
    val nestedTableCount: Int = 0
)
