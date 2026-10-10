package com.tosin.docprocessor.domain.pagination

import com.tosin.docprocessor.domain.model.DocumentElement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowControllerTest {
    private val flowController = FlowController()

    @Test
    fun `oversized block should move to its own page`() {
        assertTrue(flowController.shouldMoveToOwnPage(elementHeight = 700f, bodyHeight = 700f))
    }

    @Test
    fun `small block should not move to its own page`() {
        assertFalse(flowController.shouldMoveToOwnPage(elementHeight = 100f, bodyHeight = 700f))
    }

    @Test
    fun `zero body height never forces own page`() {
        assertFalse(flowController.shouldMoveToOwnPage(elementHeight = 100f, bodyHeight = 0f))
    }

    @Test
    fun `page break element is a forced break`() {
        assertTrue(flowController.isPageBreak(DocumentElement.PageBreak()))
    }

    @Test
    fun `image is atomic and cannot split`() {
        assertTrue(
            flowController.shouldNotSplit(
                DocumentElement.Image(sourceUri = "/tmp/x.png", altText = null)
            )
        )
    }
}
