package com.tosin.docprocessor.data.parser.util

import android.util.Log

object TDocLogger {
    private const val TAG = "TDocParser"

    fun debug(message: String) {
        Log.d(TAG, message)
    }

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null, extra: Map<String, Any?>? = null) {
        val extraInfo = extra?.let { " | Extra: $it" } ?: ""
        Log.e(TAG, "$message$extraInfo", throwable)
    }
}
