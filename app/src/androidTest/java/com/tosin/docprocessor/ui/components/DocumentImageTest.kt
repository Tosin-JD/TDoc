package com.tosin.docprocessor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DocumentImageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun bitmap(width: Int, height: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return bmp.asImageBitmap()
    }

    @Test
    fun wideImagePreservesAspectRatio() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(800.dp)) {
                DocumentImage(imageBitmap = bitmap(400, 300), caption = null, altText = "alt")
            }
        }

        val node = composeTestRule.onNodeWithTag("document-image")
        node.assertExists()
        val size = node.fetchSemanticsNode().size
        val ratio = size.width.toFloat() / size.height.toFloat()
        assertEquals(400f / 300f, ratio, 0.05f)
    }

    @Test
    fun smallImageIsNotUpscaled() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(800.dp)) {
                DocumentImage(imageBitmap = bitmap(100, 50), caption = null, altText = "alt")
            }
        }

        val imageSize = composeTestRule.onNodeWithTag("document-image").fetchSemanticsNode().size
        val containerSize = composeTestRule.onRoot().fetchSemanticsNode().size
        assertTrue(imageSize.width < containerSize.width)
        assertEquals(100f / 50f, imageSize.width.toFloat() / imageSize.height.toFloat(), 0.05f)
    }

    @Test
    fun tallImageIsNotDistorted() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                DocumentImage(imageBitmap = bitmap(100, 300), caption = null, altText = "alt")
            }
        }

        val size = composeTestRule.onNodeWithTag("document-image").fetchSemanticsNode().size
        val ratio = size.width.toFloat() / size.height.toFloat()
        assertEquals(100f / 300f, ratio, 0.05f)
    }

    @Test
    fun captionIsRenderedBelowImage() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(800.dp)) {
                DocumentImage(imageBitmap = bitmap(400, 300), caption = "Figure caption", altText = "alt")
            }
        }

        composeTestRule.onNodeWithTag("document-image").assertExists()
        composeTestRule.onNodeWithText("Figure caption").assertExists()
    }
}