package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.ui.editor.EditorViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PRINT_PREVIEW_SCALE = 0.82f

@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    val listState = rememberLazyListState()
    val pages by viewModel.pages.collectAsState()

    LaunchedEffect(pages, listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val mostVisibleItem = layoutInfo.visibleItemsInfo.maxByOrNull { item ->
                (minOf(item.offset + item.size, layoutInfo.viewportEndOffset) -
                    maxOf(item.offset, layoutInfo.viewportStartOffset)).coerceAtLeast(0)
            }
            mostVisibleItem?.index?.plus(1) to pages.size.coerceAtLeast(1)
        }
            .distinctUntilChanged()
            .collect { (currentPage, pageCount) ->
                viewModel.onPrintPageChanged(currentPage ?: 1, pageCount)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkMode) Color(0xFF1E1E1E) else Color(0xFFE4E4E4)),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(
                items = pages,
                key = { page -> page.stableKey.ifEmpty { page.index } }
            ) { page ->
                CanvasPageView(
                    pageModel = page,
                    pageCount = pages.size,
                    viewModel = viewModel,
                    isEditable = isEditable,
                    scale = PRINT_PREVIEW_SCALE,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}
