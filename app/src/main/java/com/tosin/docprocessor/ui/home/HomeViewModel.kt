package com.tosin.docprocessor.ui.home

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.entities.RecentFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class DocumentSortOption {
    LAST_MODIFIED,
    LAST_OPENED,
    NAME
}

enum class DocumentLayoutMode {
    LIST,
    GRID
}

data class HomeDocumentItem(
    val uri: String,
    val name: String,
    val mimeType: String,
    val lastOpened: Long,
    val lastModified: Long?,
    val isStarred: Boolean
)

data class HomeUiState(
    val searchQuery: String = "",
    val sortOption: DocumentSortOption = DocumentSortOption.LAST_OPENED,
    val layoutMode: DocumentLayoutMode = DocumentLayoutMode.LIST,
    val documents: List<HomeDocumentItem> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    recentFileDao: RecentFileDao
) : ViewModel() {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val query = MutableStateFlow("")
    private val sortOption = MutableStateFlow(DocumentSortOption.LAST_OPENED)
    private val layoutMode = MutableStateFlow(DocumentLayoutMode.LIST)
    private val starredUris = MutableStateFlow(loadStarredUris())

    val uiState: StateFlow<HomeUiState> = combine(
        recentFileDao.getRecentFiles(),
        query,
        sortOption,
        layoutMode,
        starredUris
    ) { recentFiles, currentQuery, currentSort, currentLayout, starred ->
        withContext(Dispatchers.IO) {
            val documents = recentFiles
                .map { resolveRecentFile(it, starred.contains(it.uri)) }
                .filter { item ->
                    item.mimeType == MimeTypes.DOCX || item.mimeType == MimeTypes.ODT ||
                        item.name.endsWith(".docx", ignoreCase = true) ||
                        item.name.endsWith(".odt", ignoreCase = true)
                }
                .filter { item ->
                    currentQuery.isBlank() || item.name.contains(currentQuery, ignoreCase = true)
                }
                .sortedWith(sortComparator(currentSort))

            HomeUiState(
                searchQuery = currentQuery,
                sortOption = currentSort,
                layoutMode = currentLayout,
                documents = documents
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState()
    )

    fun updateSearchQuery(value: String) {
        query.value = value
    }

    fun updateSortOption(option: DocumentSortOption) {
        sortOption.value = option
    }

    fun toggleLayoutMode() {
        layoutMode.update { current ->
            if (current == DocumentLayoutMode.LIST) DocumentLayoutMode.GRID else DocumentLayoutMode.LIST
        }
    }

    fun toggleStarred(uri: String) {
        val updated = starredUris.value.toMutableSet().apply {
            if (!add(uri)) remove(uri)
        }
        starredUris.value = updated
        preferences.edit().putStringSet(KEY_STARRED_URIS, updated).apply()
    }

    private fun loadStarredUris(): Set<String> =
        preferences.getStringSet(KEY_STARRED_URIS, emptySet()).orEmpty()

    private fun resolveRecentFile(file: RecentFile, isStarred: Boolean): HomeDocumentItem {
        val uri = Uri.parse(file.uri)
        val resolvedName = queryDisplayName(uri).ifBlank { file.fileName.ifBlank { "Untitled" } }
        val resolvedMimeType = queryMimeType(uri).ifBlank {
            file.mimeType.ifBlank {
                MimeTypes.fromExtension(resolvedName.substringAfterLast('.', "")) ?: ""
            }
        }
        return HomeDocumentItem(
            uri = file.uri,
            name = resolvedName,
            mimeType = resolvedMimeType,
            lastOpened = file.lastAccessed,
            lastModified = queryLastModified(uri),
            isStarred = isStarred
        )
    }

    private fun queryDisplayName(uri: Uri): String = querySingleString(uri, OpenableColumns.DISPLAY_NAME)

    private fun queryMimeType(uri: Uri): String =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: querySingleString(uri, DocumentsContract.Document.COLUMN_MIME_TYPE)

    private fun queryLastModified(uri: Uri): Long? =
        querySingleLong(uri, DocumentsContract.Document.COLUMN_LAST_MODIFIED)

    private fun querySingleString(uri: Uri, column: String): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index != -1 && cursor.moveToFirst()) cursor.getString(index).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun querySingleLong(uri: Uri, column: String): Long? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(column)
                if (index != -1 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun sortComparator(option: DocumentSortOption): Comparator<HomeDocumentItem> =
        when (option) {
            DocumentSortOption.LAST_MODIFIED -> compareByDescending<HomeDocumentItem> { it.lastModified ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            DocumentSortOption.LAST_OPENED -> compareByDescending<HomeDocumentItem> { it.lastOpened }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            DocumentSortOption.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

    private companion object {
        const val PREFS_NAME = "tdoc_home"
        const val KEY_STARRED_URIS = "starred_uris"
    }
}
