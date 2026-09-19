package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.SectionType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark

class DocxStructureParserTest {

    @Test
    fun testBodySectionTypeMapsToNextPage() {
        val document = XWPFDocument()
        val sectPr = document.document.body.addNewSectPr()
        sectPr.addNewType().`val` = STSectionMark.NEXT_PAGE
        val pgSz = sectPr.addNewPgSz()
        pgSz.w = 12240
        pgSz.h = 15840

        val section = DocxStructureParser()
            .parseDocumentLevelElements(document)
            .filterIsInstance<DocumentElement.Section>()
            .single()

        assertEquals(SectionType.NEXT_PAGE, section.properties.sectionType)
        assertEquals("nextPage", section.properties.type)
        assertEquals(12240, section.properties.pageWidth)
        assertEquals(15840, section.properties.pageHeight)
        assertEquals("body", section.properties.source)
    }

    @Test
    fun testParagraphSectionTypeMapsToContinuous() {
        val document = XWPFDocument()
        val paragraph = document.createParagraph()
        val sectPr = paragraph.ctp.addNewPPr().addNewSectPr()
        sectPr.addNewType().`val` = STSectionMark.CONTINUOUS

        val section = DocxStructureParser()
            .parseDocumentLevelElements(document)
            .filterIsInstance<DocumentElement.Section>()
            .single()

        assertEquals(SectionType.CONTINUOUS, section.properties.sectionType)
        assertEquals("paragraph:0", section.properties.source)
    }

    @Test
    fun testMissingSectionTypeDefaultsToUnknown() {
        val document = XWPFDocument()
        document.document.body.addNewSectPr()

        val section = DocxStructureParser()
            .parseDocumentLevelElements(document)
            .filterIsInstance<DocumentElement.Section>()
            .single()

        assertEquals(SectionType.UNKNOWN, section.properties.sectionType)
    }
}