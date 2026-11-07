package com.tosin.docprocessor.domain.model.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class PageDimensionsTest {
    @Test
    fun `bodyHeight excludes header and footer zones`() {
        val dims = PageDimensions(marginTop = 36f, headerEdgePt = 36f, headerHeight = 36f, marginBottom = 36f, footerEdgePt = 36f, footerHeight = 24f)
        assertEquals(dims.height - 72f - 60f, dims.bodyHeight, 0.01f)
    }

    @Test
    fun `bodyTop is marginTop plus headerHeight`() {
        val dims = PageDimensions(marginTop = 72f, headerEdgePt = 36f, headerHeight = 72f)
        assertEquals(108f, dims.bodyTop, 0.01f)
    }

    @Test
    fun `bodyRect left equals marginLeft`() {
        val dims = PageDimensions.A4
        assertEquals(dims.marginLeft, dims.bodyRect.left, 0.01f)
    }

    @Test
    fun `bodyRect width equals printableWidth`() {
        val dims = PageDimensions.A4
        assertEquals(dims.printableWidth, dims.bodyRect.width(), 0.01f)
    }

    @Test
    fun `footer rect ends at margin bottom`() {
        val dims = PageDimensions(footerHeight = 24f)
        assertEquals(dims.height - dims.footerEdgePt, dims.footerRect.bottom, 0.01f)
    }
}
