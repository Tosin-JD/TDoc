package com.tosin.docprocessor.domain.pagination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageScaleCalculatorTest {

    @Test
    fun `wide image scales down to available width, preserving ratio`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 4000f,
            intrinsicHeight = 3000f,
            availableWidth = 500f,
            maxHeight = 600f
        )
        assertEquals(500f, result.displayWidth, 0.01f)
        assertEquals(375f, result.displayHeight, 0.01f)
        assertFalse(result.isFallback)
    }

    @Test
    fun `small image is never upscaled`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 100f,
            intrinsicHeight = 50f,
            availableWidth = 500f,
            maxHeight = 600f
        )
        assertEquals(100f, result.displayWidth, 0.01f)
        assertEquals(50f, result.displayHeight, 0.01f)
        assertFalse(result.isFallback)
    }

    @Test
    fun `tall image is capped by maxHeight, ratio preserved`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 1000f,
            intrinsicHeight = 3000f,
            availableWidth = 500f,
            maxHeight = 600f
        )
        assertEquals(200f, result.displayWidth, 0.01f)
        assertEquals(600f, result.displayHeight, 0.01f)
        assertFalse(result.isFallback)
    }

    @Test
    fun `square image respects height cap`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 800f,
            intrinsicHeight = 800f,
            availableWidth = 500f,
            maxHeight = 400f
        )
        assertEquals(400f, result.displayWidth, 0.01f)
        assertEquals(400f, result.displayHeight, 0.01f)
        assertFalse(result.isFallback)
    }

    @Test
    fun `uncapped image only fits horizontally`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 800f,
            intrinsicHeight = 100f,
            availableWidth = 400f,
            maxHeight = 0f
        )
        assertEquals(400f, result.displayWidth, 0.01f)
        assertEquals(50f, result.displayHeight, 0.01f)
        assertFalse(result.isFallback)
    }

    @Test
    fun `missing intrinsic dimensions fall back to a fixed box`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = null,
            intrinsicHeight = null,
            availableWidth = 500f,
            maxHeight = 600f
        )
        assertEquals(500f, result.displayWidth, 0.01f)
        assertEquals(ImageLayoutConfig.Default.fallbackHeightPt, result.displayHeight, 0.01f)
        assertTrue(result.isFallback)
    }

    @Test
    fun `zero or negative intrinsic dimensions are treated as fallback`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 0f,
            intrinsicHeight = -5f,
            availableWidth = 500f,
            maxHeight = 600f
        )
        assertTrue(result.isFallback)
        assertEquals(500f, result.displayWidth, 0.01f)
        assertEquals(ImageLayoutConfig.Default.fallbackHeightPt, result.displayHeight, 0.01f)
    }

    @Test
    fun `zero available width produces empty size`() {
        val result = ImageScaleCalculator.scale(
            intrinsicWidth = 100f,
            intrinsicHeight = 50f,
            availableWidth = 0f,
            maxHeight = 600f
        )
        assertEquals(0f, result.displayWidth, 0.00f)
        assertEquals(0f, result.displayHeight, 0.00f)
        assertTrue(result.isFallback)
    }
}