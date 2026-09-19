package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.math.BigInteger

class DocxListParser {

    fun getListLabel(paragraph: XWPFParagraph): String? {
        val numbering = paragraph.document.numbering ?: return null

        // numID returns BigInteger? usually. We check for null or -1 (no numbering)
        val numID: BigInteger = paragraph.numID ?: return null
        if (numID == BigInteger.valueOf(-1)) return null

        val num = numbering.getNum(numID) ?: return null

        // Error Fix: Accessing 'val' keyword and CT objects
        val ctNum = num.ctNum
        val abstractNumID = ctNum.abstractNumId?.`val` ?: return null

        val abstractNum = numbering.getAbstractNum(abstractNumID) ?: return null

        // Find the specific level
        val level: BigInteger = paragraph.numIlvl ?: BigInteger.ZERO

        // Error Fix: getLvlArray is a Java method, use correct indexing or getter
        val abstractNumRaw = abstractNum.abstractNum
        val levelConf = abstractNumRaw.getLvlArray(level.toInt()) ?: return null

        // Error Fix: 'val' is a reserved keyword in Kotlin
        val format = levelConf.numFmt?.`val`?.toString() ?: "bullet"

        // levelText (lvlText/@val) contains the display label as Word emits it
        // (e.g. "1.", "a)", "i."), so prefer it over reformatting.
        val levelText = levelConf.lvlText?.`val`
        val ilvl = paragraph.numIlvl?.toInt() ?: 0
        // numLevelText is optional in OOXML; its getter throws when absent,
        // so fall back to the list-item level (1-based) for display indexing.
        val index = runCatching { paragraph.numLevelText.toIntOrNull() }
            .getOrNull()
            ?: (ilvl + 1)

        return when (format) {
            "bullet" -> levelText ?: "\u2022"
            "decimal", "lowerRoman", "upperRoman", "lowerLetter", "upperLetter" ->
                levelText ?: when (format) {
                    "decimal" -> "${paragraph.numLevelText}."
                    "lowerRoman" -> toRoman(index).lowercase()
                    "upperRoman" -> toRoman(index).uppercase()
                    "lowerLetter" -> ('a' + (index - 1)).toString()
                    "upperLetter" -> ('A' + (index - 1)).toString()
                    else -> "\u2022"
                }
            else -> levelText ?: "\u2022"
        }
    }

    private fun toRoman(value: Int): String {
        if (value <= 0 || value >= 4000) return value.toString()
        val numerals = arrayOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
            100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var remaining = value
        val builder = StringBuilder()
        for ((number, numeral) in numerals) {
            while (remaining >= number) {
                builder.append(numeral)
                remaining -= number
            }
        }
        return builder.toString()
    }

    fun parseListInfo(paragraph: XWPFParagraph): ListInfo? {
        val numbering = paragraph.document.numbering ?: return null
        val numID: BigInteger = paragraph.numID ?: return null
        if (numID == BigInteger.valueOf(-1)) return null

        val num = numbering.getNum(numID) ?: return null
        val abstractNumID = num.ctNum.abstractNumId?.`val` ?: return null
        val abstractNum = numbering.getAbstractNum(abstractNumID) ?: return null
        val level = paragraph.numIlvl ?: BigInteger.ZERO
        val levelConf = abstractNum.abstractNum.getLvlArray(level.toInt()) ?: return null
        val format = levelConf.numFmt?.`val`?.toString()
        val bulletFont = levelConf.rPr
            ?.takeIf { it.sizeOfRFontsArray() > 0 }
            ?.getRFontsArray(0)
            ?.ascii

        return ListInfo(
            level = level.toInt(),
            format = format,
            levelText = levelConf.lvlText?.`val`,
            startOverride = paragraph.numStartOverride?.toInt(),
            bulletFont = bulletFont
        )
    }
}
