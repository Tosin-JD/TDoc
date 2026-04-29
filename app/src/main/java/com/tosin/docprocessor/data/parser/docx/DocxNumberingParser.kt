package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.parser.internal.models.ListInfo
import org.w3c.dom.Document
import org.w3c.dom.Element

class DocxNumberingParser {

    data class NumberingLevel(
        val ilvl: Int,
        val format: String?,
        val levelText: String?,
        val bulletFont: String?
    )

    data class AbstractNum(
        val id: String,
        val levels: Map<Int, NumberingLevel>
    )

    data class NumInstance(
        val id: String,
        val abstractNumId: String
    )

    private val abstractNums = mutableMapOf<String, AbstractNum>()
    private val numInstances = mutableMapOf<String, NumInstance>()

    fun parse(document: Document?) {
        if (document == null) return
        val root = document.documentElement

        root.children("abstractNum").forEach { absNum ->
            val id = absNum.attribute("abstractNumId") ?: return@forEach
            val levels = mutableMapOf<Int, NumberingLevel>()
            absNum.children("lvl").forEach { lvl ->
                val ilvl = lvl.attribute("ilvl")?.toIntOrNull() ?: return@forEach
                levels[ilvl] = NumberingLevel(
                    ilvl = ilvl,
                    format = lvl.firstChild("numFmt")?.attribute("val"),
                    levelText = lvl.firstChild("lvlText")?.attribute("val"),
                    bulletFont = lvl.firstChild("rPr")?.firstChild("rFonts")?.attribute("ascii")
                )
            }
            abstractNums[id] = AbstractNum(id, levels)
        }

        root.children("num").forEach { num ->
            val id = num.attribute("numId") ?: return@forEach
            val abstractNumId = num.firstChild("abstractNumId")?.attribute("val") ?: return@forEach
            numInstances[id] = NumInstance(id, abstractNumId)
        }
    }

    fun getListInfo(numId: String?, ilvl: String?): ListInfo? {
        val nId = numId ?: return null
        val level = ilvl?.toIntOrNull() ?: 0
        val instance = numInstances[nId] ?: return null
        val abstractNum = abstractNums[instance.abstractNumId] ?: return null
        val numberingLevel = abstractNum.levels[level]

        return ListInfo(
            level = level,
            format = numberingLevel?.format,
            levelText = numberingLevel?.levelText,
            bulletFont = numberingLevel?.bulletFont
        )
    }
}
