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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.rendering.BitmapImageDimensionsProvider
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.layout.PageDimensions
import com.tosin.docprocessor.domain.model.layout.PageModel
import com.tosin.docprocessor.domain.pagination.FlowController
import com.tosin.docprocessor.domain.pagination.LayoutRegistry
import com.tosin.docprocessor.domain.pagination.PrintPaginator
import com.tosin.docprocessor.domain.pagination.SectionResolver
import com.tosin.docprocessor.domain.pagination.TextMeasurer
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

private const val PRINT_PREVIEW_SCALE = 0.82f

@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val unitConverter = remember(context) { UnitConverter(context) }
    val textMeasurer = remember(context, unitConverter) { TextMeasurer(context, unitConverter) }
    val layoutRegistry = remember { LayoutRegistry() }
    val flowController = remember(textMeasurer, unitConverter) { FlowController(textMeasurer, unitConverter) }
    val imageDimensionsProvider = remember { BitmapImageDimensionsProvider() }
    val paginator = remember(
        textMeasurer, unitConverter, layoutRegistry, flowController, imageDimensionsProvider
    ) {
        PrintPaginator(
            textMeasurer, unitConverter, layoutRegistry, flowController, imageDimensionsProvider
        )
    }
    val renderer = remember(unitConverter) { PrintElementRenderer(unitConverter) }
    val sectionResolver = remember { SectionResolver() }
    val listState = rememberLazyListState()

    val currentDocument by viewModel.currentDocument.collectAsState()
    val documentData = remember(currentDocument, viewModel.documentElements) {
        currentDocument?.copy(content = viewModel.documentElements)
            ?: DocumentData(content = viewModel.documentElements)
    }

    val pagesState = remember { mutableStateOf<List<PageModel>>(emptyList()) }
    val pages = pagesState.value
    
    LaunchedEffect(documentData, isDarkMode) {
        delay(200)
        val result = withContext(Dispatchers.Default) {
            val sections = documentData.content.filterIsInstance<DocumentElement.Section>()
            val defaultDimensions = if (sections.isNotEmpty()) {
                // Use first section as base; paginator will handle subsequent sections
                sectionResolver.resolve(sections.first().properties)
            } else {
                PageDimensions.A4
            }

            paginator.paginate(documentData, defaultDimensions, isDarkMode = isDarkMode)
        }
        // Generate stable keys for pages
        val pagesWithKeys = result.mapIndexed { idx, page ->
            val firstElementId = page.elements.firstOrNull()?.elementId ?: ""
            val contentHash = page.elements.fold(0) { acc, elem -> acc + elem.elementId.hashCode() }
            page.copy(
                stableKey = "${firstElementId}_${contentHash}_${idx}",
                documentFingerprint = documentData.hashCode().toLong()
            )
        }
        pagesState.value = pagesWithKeys
    }

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
                PageView(
                    pageModel = page,
                    pageCount = pages.size,
                    renderer = renderer,
                    unitConverter = unitConverter,
                    viewModel = viewModel,
                    isEditable = isEditable,
                    scale = PRINT_PREVIEW_SCALE,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}
