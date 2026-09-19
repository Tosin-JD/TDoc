package com.tosin.docprocessor.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.model.isTextBlock
import com.tosin.docprocessor.ui.components.TableWidget
import java.io.File

/**
 * Decodes an image bounded by the display width using power-of-two downsampling,
 * so a huge embedded image never materialises at full resolution in memory.
 */
private fun decodeImageDownsampled(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val targetWidth = 1080
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetWidth) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

/**
 * Renders a block: editable text goes through [EditorParagraphBlock]; everything
 * else is shown read-only so the flow stays intact for round-trip saving.
 */
@Composable
fun EditorBlockRenderer(
    block: EditorBlock,
    focusRequestBlockId: Long?,
    onTextChange: (Long, List<TextSpan>) -> Unit,
    onSelectionChange: (Long, Selection) -> Unit,
    onEnterPressed: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        block.type.isTextBlock -> EditorParagraphBlock(
            block = block,
            focusRequestBlockId = focusRequestBlockId,
            onTextChange = onTextChange,
            onSelectionChange = onSelectionChange,
            onEnterPressed = onEnterPressed,
            onUndo = onUndo,
            onRedo = onRedo,
            modifier = modifier
        )
        else -> NonTextBlock(block, modifier)
    }
}

@Composable
private fun NonTextBlock(block: EditorBlock, modifier: Modifier = Modifier) {
    val element = block.element
    when (val target = element) {
        is DocumentElement.Table -> TableWidget(
            rows = target.rows,
            modifier = modifier.padding(vertical = 8.dp)
        )

        is DocumentElement.Image -> {
            val imageBitmap = remember(target.sourceUri) {
                decodeImageDownsampled(target.sourceUri)
            }
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = target.altText,
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = target.caption ?: target.altText ?: "Image unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(vertical = 8.dp)
                )
            }
        }

        DocumentElement.PageBreak -> HorizontalDivider(
            modifier = modifier.padding(vertical = 16.dp)
        )

        else -> if (target != null) {
            MetadataBlock(
                element = target,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MetadataBlock(element: DocumentElement, modifier: Modifier = Modifier) {
    val description = remember(element) { element.describe() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun DocumentElement.describe(): String = when (this) {
    is DocumentElement.SectionHeader -> text
    is DocumentElement.Section -> "Page section ${properties.sectionIndex}: ${properties.columnCount?.let { "$it columns" } ?: "single column"}"
    is DocumentElement.HeaderFooter -> "${content.kind.name.lowercase()} (${content.variant}): ${content.text}"
    is DocumentElement.Note -> "${info.kind.name.lowercase()}: ${info.text}"
    is DocumentElement.Comment -> "Comment by ${info.author ?: "unknown"}: ${info.text}"
    is DocumentElement.Bookmark -> "Bookmark ${info.boundary.name.lowercase()}: ${info.name.ifBlank { info.id }}"
    is DocumentElement.Field -> "Field ${info.type}: ${info.instruction}"
    is DocumentElement.Metadata -> buildString {
        append(info.title ?: info.kind)
        append(": ")
        append(info.summary)
    }
    is DocumentElement.Drawing -> "Drawing: ${info.kind}"
    is DocumentElement.EmbeddedObject -> "Embedded object: ${info.programId ?: info.kind}"
    else -> ""
}