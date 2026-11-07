package com.tosin.docprocessor.data.writer

import com.tosin.docprocessor.domain.model.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentOutputFormatResolverTest {
    @Test
    fun extensionSelectsDocumentFormatWhenProviderMimeIsGeneric() {
        assertEquals(MimeTypes.DOCX, DocumentOutputFormatResolver.resolve("Report.DOCX", "application/octet-stream"))
        assertEquals(MimeTypes.ODT, DocumentOutputFormatResolver.resolve("Report.odt", "application/octet-stream"))
    }

    @Test
    fun recognizedMimeTypeIsUsedWhenExtensionIsMissingOrUnknown() {
        assertEquals(MimeTypes.DOCX, DocumentOutputFormatResolver.resolve("Report", MimeTypes.DOCX))
        assertEquals(MimeTypes.ODT, DocumentOutputFormatResolver.resolve("Report.data", MimeTypes.ODT))
    }

    @Test
    fun textOutputIsExplicitAndUnsupportedTypesAreRejected() {
        assertEquals("text/plain", DocumentOutputFormatResolver.resolve("notes.txt", "application/octet-stream"))
        assertEquals(MimeTypes.DOCX, DocumentOutputFormatResolver.resolve("notes.docx", "text/plain"))
        assertThrows(IllegalArgumentException::class.java) {
            DocumentOutputFormatResolver.resolve("notes.unknown", "application/octet-stream")
        }
    }
}
