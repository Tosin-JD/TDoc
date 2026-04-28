package com.tosin.docprocessor.data.parser.validation

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.util.TDocLogger

class DocumentValidator {

    fun validate(elements: List<DocumentElement>): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (elements.isEmpty()) {
            errors.add(ValidationError("Document is empty", ValidationError.Severity.WARNING))
        }

        elements.forEachIndexed { index, element ->
            when (element) {
                is DocumentElement.Paragraph -> {
                    if (element.spans.isEmpty() && element.listLabel == null) {
                        // Empty paragraphs are common, but we might want to flag them if they are unusual
                    }
                }
                is DocumentElement.Image -> {
                    if (element.sourceUri.isBlank()) {
                        errors.add(ValidationError("Image at index $index has no source URI", ValidationError.Severity.ERROR))
                    }
                }
                is DocumentElement.Table -> {
                    if (element.rows.isEmpty()) {
                        errors.add(ValidationError("Table at index $index has no rows", ValidationError.Severity.WARNING))
                    }
                }
                else -> {}
            }
        }

        return if (errors.any { it.severity == ValidationError.Severity.ERROR || it.severity == ValidationError.Severity.FATAL }) {
            ValidationResult.failure(errors)
        } else {
            if (errors.isNotEmpty()) {
                TDocLogger.warn("Validation warnings: ${errors.joinToString { it.message }}")
            }
            ValidationResult.SUCCESS
        }
    }
}
