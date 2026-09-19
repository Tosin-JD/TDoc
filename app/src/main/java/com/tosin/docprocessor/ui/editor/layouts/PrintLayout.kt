package com.tosin.docprocessor.ui.editor.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.ui.editor.EditorBlockRenderer

/**
 * Print-style preview: an A4-like sheet centered on a grey canvas.
 *
 * The paper page itself is intentionally fixed [Color.White] (print metaphor);
 * the surrounding workspace uses theme tokens.
 */
@Composable
fun PrintLayout(
    blocks: List<EditorBlock>,
    focusRequestBlockId: Long?,
    onTextChange: (Long, List<TextSpan>) -> Unit,
    onSelectionChange: (Long, Selection) -> Unit,
    onEnterPressed: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .width(595.dp)
                .shadow(8.dp)
                .background(Color.White)
                .defaultMinSize(minHeight = 842.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 72.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                blocks.forEach { block ->
                    EditorBlockRenderer(
                        block = block,
                        focusRequestBlockId = focusRequestBlockId,
                        onTextChange = onTextChange,
                        onSelectionChange = onSelectionChange,
                        onEnterPressed = onEnterPressed,
                        onUndo = onUndo,
                        onRedo = onRedo
                    )
                }
            }
        }
    }
}