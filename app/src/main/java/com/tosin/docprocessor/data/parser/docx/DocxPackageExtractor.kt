package com.tosin.docprocessor.data.parser.docx

import com.tosin.docprocessor.data.common.model.DocumentElement
import org.apache.poi.xwpf.usermodel.XWPFDocument

/**
 * Extracts document-level elements from a DOCX package that are not part of the
 * main body content (paragraphs, tables, etc.).
 *
 * Implementations are called after the body elements are parsed, in the order
 * they are provided to [DocxParser].
 *
 * ## Extending
 *
 * To add a new extraction pass:
 * 1. Implement this interface.
 * 2. Add your implementation to the [packageExtractors] list in [DocxParser].
 *
 * For dependency-injected extractors (e.g. an extractor that needs Context or
 * other Android types), provide them via Hilt and compose them into [DocxParser]
 * through its constructor.
 *
 * ## Threading
 *
 * This is called on Dispatchers.IO from [DocxParser.parse]. Blocking operations
 * are acceptable but keep them short — the DOCX is already in memory as a byte
 * array.
 */
interface DocxPackageExtractor {
    /**
     * Extracts elements from the DOCX package.
     * @return a list of [DocumentElement] instances, possibly empty.
     */
    fun extract(document: XWPFDocument, docxPackage: DocxPackage): List<DocumentElement>
}