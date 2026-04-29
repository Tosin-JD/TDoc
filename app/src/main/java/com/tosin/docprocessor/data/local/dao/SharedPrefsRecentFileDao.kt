package com.tosin.docprocessor.data.local.dao

import android.content.Context
import com.tosin.docprocessor.data.local.entities.RecentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SharedPrefsRecentFileDao @Inject constructor(
    @param:ApplicationContext private val context: Context
) : RecentFileDao {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val entries = MutableStateFlow(loadEntries())

    override fun getRecentFiles(): Flow<List<RecentFile>> = entries.map { it.take(MAX_VISIBLE_FILES) }

    override suspend fun insertFile(file: RecentFile) {
        val updatedEntries = entries.value
            .filterNot { it.uri == file.uri }
            .plus(file.copy(lastAccessed = System.currentTimeMillis()))
            .sortedByDescending { it.lastAccessed }
            .take(MAX_STORED_FILES)

        persist(updatedEntries)
    }

    override suspend fun pruneOldFiles(): Int {
        val currentEntries = entries.value
        val trimmedEntries = currentEntries
            .sortedByDescending { it.lastAccessed }
            .take(MAX_STORED_FILES)
        persist(trimmedEntries)
        return currentEntries.size - trimmedEntries.size
    }

    private fun persist(updatedEntries: List<RecentFile>) {
        entries.value = updatedEntries
        val jsonArray = JSONArray()
        updatedEntries.forEach { file ->
            jsonArray.put(
                JSONObject()
                    .put("uri", file.uri)
                    .put("fileName", file.fileName)
                    .put("mimeType", file.mimeType)
                    .put("fileSize", file.fileSize)
                    .put("lastAccessed", file.lastAccessed)
            )
        }
        preferences.edit().putString(KEY_RECENT_FILES, jsonArray.toString()).apply()
    }

    private fun loadEntries(): List<RecentFile> = runCatching {
        val rawJson = preferences.getString(KEY_RECENT_FILES, null) ?: return emptyList()
        val jsonArray = JSONArray(rawJson)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    RecentFile(
                        uri = item.optString("uri"),
                        fileName = item.optString("fileName"),
                        mimeType = item.optString("mimeType"),
                        fileSize = item.optLong("fileSize"),
                        lastAccessed = item.optLong("lastAccessed")
                    )
                )
            }
        }.sortedByDescending { it.lastAccessed }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS_NAME = "tdoc_recent_files"
        const val KEY_RECENT_FILES = "recent_files"
        const val MAX_VISIBLE_FILES = 5
        const val MAX_STORED_FILES = 20
    }
}
