package com.tosin.docprocessor.data.parser.docx.mapper

import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.docx.DocxPackage
import com.tosin.docprocessor.data.parser.docx.opc.OpcPackage
import com.tosin.docprocessor.data.parser.docx.resolve.DocxNumberingResolver
import com.tosin.docprocessor.data.parser.docx.resolve.DocxStyleResolver

/** Shared state passed to every DOCX mapper. Mappers never write files. */
class DocxMapContext(
    val part: String,
    val opc: OpcPackage,
    val styles: DocxStyleResolver,
    val numbering: DocxNumberingResolver,
    val resources: ResourceCollector,
    val diagnostics: DiagnosticsCollector
) {
    fun relationships(): Map<String, DocxPackage.Relationship> = opc.relationshipsFor(part)

    fun relationshipBySuffix(suffix: String): DocxPackage.Relationship? =
        relationships().values.firstOrNull {
            it.type?.substringAfterLast('/')?.equals(suffix, ignoreCase = true) == true
        }
}
