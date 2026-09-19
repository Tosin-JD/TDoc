package com.tosin.docprocessor.ui.navigation

import android.net.Uri
import com.tosin.docprocessor.model.DocumentMeta

object TDocRoutes {
    const val HOME = "home"
    const val EDITOR = "editor"

    const val ARG_OPEN_MODE = "openMode"
    const val ARG_URI = "uri"
    const val ARG_MIME = "mime"
    const val ARG_SAVE_AS_NAME = "saveAsName"

    fun editorRoute(
        openMode: String,
        uri: String,
        mime: String,
        saveAsName: String
    ): String =
        "$EDITOR?$ARG_OPEN_MODE=${Uri.encode(openMode)}" +
            "&$ARG_URI=${Uri.encode(uri)}" +
            "&$ARG_MIME=${Uri.encode(mime)}" +
            "&$ARG_SAVE_AS_NAME=${Uri.encode(saveAsName)}"

    fun editorRoute(meta: DocumentMeta, openMode: String, saveAsName: String): String =
        editorRoute(openMode, meta.uri, meta.mimeType, saveAsName)
}