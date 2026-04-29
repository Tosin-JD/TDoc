package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.domain.pagination.FlowController
import com.tosin.docprocessor.domain.pagination.LayoutRegistry
import com.tosin.docprocessor.domain.pagination.PrintPaginator
import com.tosin.docprocessor.domain.pagination.TextMeasurer
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.core.rendering.PrintElementRenderer

@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester
) {
    val context = LocalContext.current
    val unitConverter = remember(context) { UnitConverter(context) }
    val textMeasurer = remember(context, unitConverter) { TextMeasurer(context, unitConverter) }
    val layoutRegistry = remember { LayoutRegistry() }
    val flowController = remember(textMeasurer, unitConverter) { FlowController(textMeasurer, unitConverter) }
    val paginator = remember(textMeasurer, unitConverter, layoutRegistry, flowController) {
        PrintPaginator(textMeasurer, unitConverter, layoutRegistry, flowController)
    }
    val renderer = remember { PrintElementRenderer() }
    
    val currentDocument by viewModel.currentDocument.collectAsState()
    val documentData = currentDocument ?: DocumentData(content = viewModel.documentElements)
    val pages = paginator.paginate(documentData, PageDimensions.A4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pages) { page ->
                PageView(
                    pageModel = page,
                    renderer = renderer,
                    scale = 1.0f
                )
            }
        }
    }
}
