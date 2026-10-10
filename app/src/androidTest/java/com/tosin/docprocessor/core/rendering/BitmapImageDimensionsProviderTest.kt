package com.tosin.docprocessor.core.rendering

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BitmapImageDimensionsProviderTest {

    private val provider = BitmapImageDimensionsProvider()

    @Test
    fun readsIntrinsicSizeInPointsFromPixels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "dimensions_test.png")
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val dims = provider.intrinsicPoints(file.absolutePath)

        assertEquals(3.0f, dims!!.widthPt, 0.01f)
        assertEquals(1.5f, dims!!.heightPt, 0.01f)

        file.delete()
    }

    @Test
    fun missingFileReturnsNull() {
        assertNull(provider.intrinsicPoints("/nonexistent/image.png"))
    }
}