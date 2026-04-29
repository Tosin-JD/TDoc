package com.tosin.docprocessor.data.parser.util

import android.util.Log

object TDocLogger {
    private const val TAG = "TDocParser"

    fun debug(message: String) {
        safeLog("DEBUG", message) { Log.d(TAG, message) }
    }

    fun info(message: String) {
        safeLog("INFO", message) { Log.i(TAG, message) }
    }

    fun warn(message: String, throwable: Throwable? = null) {
        safeLog("WARN", message, throwable) { Log.w(TAG, message, throwable) }
    }

    fun error(message: String, throwable: Throwable? = null, extra: Map<String, Any?>? = null) {
        val extraInfo = extra?.let { " | Extra: $it" } ?: ""
        safeLog("ERROR", "$message$extraInfo", throwable) { Log.e(TAG, "$message$extraInfo", throwable) }
    }

    private inline fun safeLog(
        level: String,
        message: String,
        throwable: Throwable? = null,
        write: () -> Unit
    ) {
        runCatching(write).getOrElse {
            println("$TAG [$level] $message")
            throwable?.printStackTrace()
        }
    }
}
