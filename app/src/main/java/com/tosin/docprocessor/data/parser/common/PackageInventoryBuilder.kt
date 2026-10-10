package com.tosin.docprocessor.data.parser.common

import com.tosin.docprocessor.domain.document.PackageEntry
import com.tosin.docprocessor.domain.document.PackageInventory

/** Keeps package inspection format-neutral and free of Android dependencies. */
object PackageInventoryBuilder {
    fun fromEntries(
        entries: Map<String, ByteArray>,
        recognizedPaths: Set<String>
    ): PackageInventory = PackageInventory(
        entries = entries.map { (path, bytes) ->
            PackageEntry(
                path = path,
                sizeBytes = bytes.size.toLong(),
                isRecognized = path in recognizedPaths
            )
        }
    )
}
