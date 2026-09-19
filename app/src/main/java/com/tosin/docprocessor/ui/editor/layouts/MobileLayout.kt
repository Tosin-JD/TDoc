package com.tosin.docprocessor.ui.editor.layouts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.EditorBlock
import com.tosin.docprocessor.ui.editor.EditorBlockRenderer

@Composable
fun MobileLayout(
    blocks: List<EditorBlock>,
    focusRequestBlockId: Long?,
    onTextChange: (Long, List<TextSpan>) -> Unit,
    onSelectionChange: (Long, Selection) -> Unit,
    onEnterPressed: (Long) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(blocks, key = { it.id }) { block ->
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