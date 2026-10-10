package com.tosin.docprocessor.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.domain.pagination.ImageLayoutConfig
import com.tosin.docprocessor.domain.pagination.ImageScaleCalculator
import java.io.File

/**
 * Convenience overload for callers that hold a source URI. Decodes the bitmap
 * (or shows a fallback) and delegates layout to [DocumentImage].
 */
@Composable
fun ImageWidget(
    sourceUri: String,
    caption: String?,
    altText: String?,
    isDarkMode: Boolean = false
) {
    val imageBitmap = remember(sourceUri) {
        File(sourceUri)
            .takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }

    if (imageBitmap != null) {
        DocumentImage(
            imageBitmap = imageBitmap,
            caption = caption,
            altText = altText,
            isDarkMode = isDarkMode
        )
    } else {
        ImageUnavailable(caption = caption, altText = altText, isDarkMode = isDarkMode)
    }
}

/**
 * Aspect-ratio-correct, undistorted image view.
 *
 * The display size is computed by [ImageScaleCalculator] from the intrinsic
 * bitmap pixels and the available width, so the image is never upscaled, never
 * stretched and never taller than the configured height cap. Small images end
 * up at their natural size, centred within the column.
 *
 * The composable is a dumb view: it owns no persistence, no decoding and no
 * document-model logic. Colours resolve from [DocumentColorTokens].
 */
@Composable
fun DocumentImage(
    imageBitmap: ImageBitmap,
    caption: String?,
    altText: String?,
    modifier: Modifier = Modifier,
    config: ImageLayoutConfig = ImageLayoutConfig.Default,
    isDarkMode: Boolean = false
) {
    val density = LocalDensity.current
    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { (maxHeight * config.maxHeightFraction).toPx() }
            val displaySize = remember(imageBitmap, maxWidth, maxHeight, density, config) {
                ImageScaleCalculator.scale(
                    intrinsicWidth = imageBitmap.width.toFloat(),
                    intrinsicHeight = imageBitmap.height.toFloat(),
                    availableWidth = availableWidthPx,
                    maxHeight = maxHeightPx
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = altText,
                    modifier = Modifier
                        .testTag("document-image")
                        .width(with(density) { displaySize.displayWidth.toDp() })
                        .height(with(density) { displaySize.displayHeight.toDp() }),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        caption?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = Color(tokens.secondaryText),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = (config.captionSpacingPt * 4f / 3f).dp)
            )
        }
    }
}

@Composable
private fun ImageUnavailable(
    caption: String?,
    altText: String?,
    isDarkMode: Boolean
) {
    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
    Text(
        text = caption ?: altText ?: "Image unavailable",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(tokens.secondaryText),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}