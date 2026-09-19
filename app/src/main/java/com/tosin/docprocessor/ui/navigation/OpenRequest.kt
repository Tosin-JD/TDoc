package com.tosin.docprocessor.ui.navigation

import com.tosin.docprocessor.ui.editor.OpenMode

/**
 * A resolved file-open request from an incoming intent (VIEW / EDIT / SEND),
 * or from the Home screen. [uri] is the content URI string.
 */
data class OpenRequest(
    val uri: String,
    val mime: String,
    val openMode: OpenMode,
    val saveAsName: String
) {
    fun toEditorRoute(): String =
        TDocRoutes.editorRoute(openMode.name, uri, mime, saveAsName)
}