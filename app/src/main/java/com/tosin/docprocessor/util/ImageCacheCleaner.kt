package com.tosin.docprocessor.util

import android.content.Context
import java.io.File

/**
 * Prunes parser-extracted image cache files older than [maxAgeMillis].
 * The 7-day threshold makes it safe against files currently open in the editor
 * (which are always freshly written).
 */
object ImageCacheCleaner {

    const val DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun prune(context: Context, maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        val cacheDir = context.cacheDir ?: return
        cacheDir.listFiles()
            ?.filter { it.isFile && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}