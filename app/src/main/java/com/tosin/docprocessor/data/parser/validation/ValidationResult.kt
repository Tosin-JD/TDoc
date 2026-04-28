package com.tosin.docprocessor.data.parser.validation

data class ValidationError(
    val message: String,
    val severity: Severity = Severity.ERROR,
    val elementId: String? = null
) {
    enum class Severity {
        WARNING, ERROR, FATAL
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
) {
    companion object {
        val SUCCESS = ValidationResult(true)
        
        fun failure(errors: List<ValidationError>) = ValidationResult(false, errors)
        fun failure(message: String) = ValidationResult(false, listOf(ValidationError(message)))
    }
}
