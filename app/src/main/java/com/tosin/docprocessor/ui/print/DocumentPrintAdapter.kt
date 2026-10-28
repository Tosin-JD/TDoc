package com.tosin.docprocessor.ui.print

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.UnitConverter
import java.io.FileOutputStream

class DocumentPrintAdapter(
    private val context: Context,
    private val pages: List<PageModel>
) : PrintDocumentAdapter() {

    private var printedDocument: PrintedPdfDocument? = null
    private val unitConverter = UnitConverter(context)

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        if (pages.isEmpty()) {
            callback.onLayoutFailed("No pages to print")
            return
        }

        val info = PrintDocumentInfo.Builder("document.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages.size)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        try {
            val document = PrintedPdfDocument(context, PrintAttributes.Builder().build())
            printedDocument = document

            pages.forEachIndexed { index, page ->
                val pdfPage = document.startPage(index)
                // Basic stub: actual rendering would require drawing the PageModel to canvas
                document.finishPage(pdfPage)
            }

            val out = FileOutputStream(destination.fileDescriptor)
            document.writeTo(out)
            out.close()
            document.close()

            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
