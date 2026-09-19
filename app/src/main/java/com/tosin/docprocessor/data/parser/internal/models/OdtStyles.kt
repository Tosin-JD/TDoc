package com.tosin.docprocessor.data.parser.internal.models

/**
 * Full ODT style system model.
 * One [OdtStyle] per `<style:style>` element, plus the default style.
 */
data class OdtStyle(
    val family: String,  // "paragraph", "text", "table", "table-cell"
    val name: String,
    val parentName: String? = null,
    val textProperties: OdtTextProperties? = null,
    val paragraphProperties: OdtParagraphProperties? = null,
    val tableProperties: OdtTableProperties? = null,
    val tableCellProperties: OdtTableCellProperties? = null,
    val isHeading: Boolean = false
)

data class OdtTextProperties(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: String? = null,        // hex without #, e.g. "FF0000"
    val fontSize: Float? = null,      // in points
    val fontFamily: String? = null,   // e.g. "Liberation Serif"
    val fontStyle: String? = null,    // "swiss", "serif", "monospace", "modern", "symbol"
    val language: String? = null,     // e.g. "en-US"
    val country: String? = null       // e.g. "US"
)

data class OdtParagraphProperties(
    val alignment: ParagraphAlignment? = null,
    val indentation: ParagraphIndentation? = null,  // reuses existing model
    val spacing: ParagraphSpacing? = null,          // reuses existing model
    val outlineLevel: Int? = null,
    val pageBreakBefore: Boolean = false,
    val keepTogether: Boolean = false,
    val border: OdtBorder? = null
)

data class OdtTableProperties(
    val tableBorder: OdtBorder? = null,
    val backgroundColor: String? = null,
    val width: String? = null,       // e.g. "10cm", "100%"
    val tableLayout: String? = null  // "fixed" / "automatic"
)

data class OdtTableCellProperties(
    val backgroundColor: String? = null,
    val border: OdtBorder? = null,
    val verticalAlignment: String? = null,  // "top" / "middle" / "bottom"
    val marginLeft: Int? = null,
    val marginRight: Int? = null,
    val marginTop: Int? = null,
    val marginBottom: Int? = null
)

data class OdtBorder(
    val width: Int? = null,          // in twips (1/1440 inch)
    val color: String? = null,       // hex without #
    val style: String? = null,      // "solid", "dashed", "dotted", "double", "none"
    val borderSide: String? = null  // "left", "right", "top", "bottom", "inside", "outside"
)