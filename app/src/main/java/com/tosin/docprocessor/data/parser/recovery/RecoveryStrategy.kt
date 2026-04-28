package com.tosin.docprocessor.data.parser.recovery

import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.util.TDocLogger

interface RecoveryStrategy {
    fun <T> handleFailure(
        context: String,
        error: Throwable,
        fallback: () -> T
    ): T
}

class GracefulDegradationStrategy : RecoveryStrategy {
    override fun <T> handleFailure(
        context: String,
        error: Throwable,
        fallback: () -> T
    ): T {
        TDocLogger.error("Recoverable failure in $context. Skipping/Degrading.", error)
        return fallback()
    }
}
