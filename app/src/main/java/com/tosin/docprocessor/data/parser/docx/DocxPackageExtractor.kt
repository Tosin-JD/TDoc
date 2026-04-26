package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFDocument

interface DocxPackageExtractor {
    fun extract(document: XWPFDocument, docxPackage: DocxPackage): List<DocumentElement>
}
