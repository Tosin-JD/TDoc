package com.tosin.docprocessor.ui.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import com.tosin.docprocessor.domain.model.layout.PageModel

class PrintHelper(private val context: Context) {
    fun printDocument(
        jobName: String,
        pages: List<PageModel>,
        attributes: PrintAttributes? = null
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = DocumentPrintAdapter(context, pages)
        val attrs = attributes ?: PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        printManager.print(jobName, adapter, attrs)
    }
}
