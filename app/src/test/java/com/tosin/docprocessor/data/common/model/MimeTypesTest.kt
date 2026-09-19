package com.tosin.docprocessor.data.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MimeTypesTest {

    @Test
    fun `fromExtension maps docx`() {
        assertEquals(MimeTypes.DOCX, MimeTypes.fromExtension("docx"))
    }

    @Test
    fun `fromExtension maps odt`() {
        assertEquals(MimeTypes.ODT, MimeTypes.fromExtension("odt"))
    }

    @Test
    fun `fromExtension maps txt`() {
        assertEquals(MimeTypes.TXT, MimeTypes.fromExtension("txt"))
    }

    @Test
    fun `fromExtension is case-insensitive`() {
        assertEquals(MimeTypes.DOCX, MimeTypes.fromExtension("DOCX"))
    }

    @Test
    fun `fromExtension returns null for unknown`() {
        assertNull(MimeTypes.fromExtension("unknown"))
    }

    @Test
    fun `fromFileName resolves document docx`() {
        assertEquals(MimeTypes.DOCX, MimeTypes.fromFileName("document.docx"))
    }

    @Test
    fun `fromFileName is case-insensitive`() {
        assertEquals(MimeTypes.TXT, MimeTypes.fromFileName("file.TXT"))
    }

    @Test
    fun `fromFileName returns null when no extension`() {
        assertNull(MimeTypes.fromFileName("noextension"))
    }

    @Test
    fun `fromFileName returns null for blank name`() {
        assertNull(MimeTypes.fromFileName(""))
    }

    @Test
    fun `isSupportedMime accepts all three formats`() {
        assertEquals(true, MimeTypes.isSupportedMime(MimeTypes.DOCX))
        assertEquals(true, MimeTypes.isSupportedMime(MimeTypes.ODT))
        assertEquals(true, MimeTypes.isSupportedMime(MimeTypes.TXT))
        assertEquals(false, MimeTypes.isSupportedMime("application/pdf"))
    }
}