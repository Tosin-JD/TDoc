package com.tosin.docprocessor.data.parser.internal.models

import com.tosin.docprocessor.domain.document.model.style.CellBorders
import com.tosin.docprocessor.domain.document.model.style.TextAlignment

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
    val gutter: Int? = null,
    val headerEdge: Int? = null,
    val footerEdge: Int? = null,
    val orientation: String? = null,
    val columnCount: Int? = null,
    val pageNumberStart: Int? = null,
    /** Document-authored page background colour (`w:background`/ODF `fo:background-color`). */
    val pageBackground: String? = null,
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
    /** Styled runs of the header/footer story; empty for legacy/plain content. */
    val spans: List<TextSpan> = emptyList(),
    val paragraphCount: Int,
    val tableCount: Int,
    val elementSummaries: List<String> = emptyList(),
    val containsWatermark: Boolean = false,
    val watermarkDescriptions: List<String> = emptyList()
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
    val name: String,
    val boundary: BookmarkBoundary = BookmarkBoundary.START,
    val source: String? = null
)

enum class BookmarkBoundary {
    START,
    END
}

data class FieldInfo(
    val type: String,
    val instruction: String,
    val value: String? = null,
    val isSimpleField: Boolean = false,
    val arguments: Map<String, String> = emptyMap(),
    val source: String? = null
)

data class MetadataInfo(
    val kind: String,
    val title: String? = null,
    val summary: String,
    val attributes: Map<String, String> = emptyMap(),
    val children: List<MetadataInfo> = emptyList(),
    val source: String? = null
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
    val hasSmartArt: Boolean = false,
    val rotation: Int? = null,
    val effects: List<String> = emptyList(),
    val isGrouped: Boolean = false,
    val hasText: Boolean = false,
    /** Authored shape fill colour (hex/`rgb()`/named) when the drawing declares one. */
    val fillColor: String? = null,
    /** Authored shape outline colour when the drawing declares one. */
    val strokeColor: String? = null
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
    val rows: List<List<TableCellMetadata>> = emptyList(),
    /** Table-level borders (`w:tblBorders`) used as a fallback for cells without their own. */
    val borders: CellBorders = CellBorders(),
    /** Table-level default cell padding/margins in points (`w:tblCellMar`). */
    val cellMarginsPt: CellBorders? = null,
    /** Table alignment (`w:jc`) mapped to the document horizontal alignment. */
    val alignment: TextAlignment? = null
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
