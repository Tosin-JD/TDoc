package com.tosin.docprocessor.ui.editor

/**
 * How the current document was opened. Drives save behaviour:
 *  - EDIT:  write straight back to the granted URI.
 *  - VIEW:  open read/write but persist via "Save a copy" (SAF create).
 *  - SEND:  contents never have a persisted home; always "Save a copy".
 */
enum class OpenMode {
    VIEW,
    EDIT,
    SEND;

    companion object {
        fun from(name: String?): OpenMode = when (name?.uppercase()) {
            "EDIT" -> EDIT
            "SEND" -> SEND
            else -> VIEW
        }
    }
}