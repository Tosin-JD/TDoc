package com.tosin.docprocessor.data.parser.docx

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

        return when (format) {
            "bullet" -> "•"
            // numLevelText is a helper that returns the actual calculated number/bullet
            "decimal" -> "${paragraph.numLevelText}."
            else -> "•"
        }
    }
}