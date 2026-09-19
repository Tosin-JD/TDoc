package com.tosin.docprocessor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.ui.editor.OpenMode
import com.tosin.docprocessor.ui.navigation.OpenRequest
import com.tosin.docprocessor.ui.navigation.TDocNavHost
import com.tosin.docprocessor.ui.theme.TDocTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val openRequest = MutableStateFlow<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openRequest.value = resolveOpenRequest(intent)
        setContent {
            val request by openRequest.collectAsStateWithLifecycle()
            TDocTheme {
                TDocNavHost(
                    initialOpenRequest = request,
                    onOpenRequestHandled = { openRequest.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveOpenRequest(intent)?.let { openRequest.value = it }
    }

    /**
     * Turns an incoming intent (VIEW / EDIT / SEND) into an [OpenRequest], or null
     * if there is nothing actionable. Falls back to the URI's MIME type / file name
     * when the intent does not carry a type.
     */
    private fun resolveOpenRequest(intent: Intent?): OpenRequest? {
        if (intent == null) return null
        val uri: Uri = intent.data
            ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: return null
        val mode = when (intent.action) {
            Intent.ACTION_EDIT -> OpenMode.EDIT
            Intent.ACTION_SEND -> OpenMode.SEND
            else -> OpenMode.VIEW
        }
        val mime = intent.type
            ?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: contentResolver.getType(uri)
            ?: MimeTypes.fromFileName(uri.lastPathSegment.orEmpty())
            ?: return null

        if (!MimeTypes.isSupportedMime(mime)) return null

        return OpenRequest(
            uri = uri.toString(),
            mime = mime,
            openMode = mode,
            saveAsName = queryDisplayName(uri)
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment ?: "Untitled"
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else fallback
            } ?: fallback
        }.getOrElse { fallback }
    }
}