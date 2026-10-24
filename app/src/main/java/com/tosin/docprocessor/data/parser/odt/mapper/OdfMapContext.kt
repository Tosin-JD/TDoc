package com.tosin.docprocessor.data.parser.odt.mapper

import com.tosin.docprocessor.data.parser.common.DiagnosticsCollector
import com.tosin.docprocessor.data.parser.common.ListNumberingState
import com.tosin.docprocessor.data.parser.common.ResourceCollector
import com.tosin.docprocessor.data.parser.odt.resolve.OdfStyleResolver

/** Shared state for ODT mappers. Never touches the filesystem. */
class OdfMapContext(
    val part: String,
    val styles: OdfStyleResolver,
    val resources: ResourceCollector,
    val diagnostics: DiagnosticsCollector,
    val entries: Map<String, ByteArray>,
    val listNumbering: ListNumberingState = ListNumberingState()
)
