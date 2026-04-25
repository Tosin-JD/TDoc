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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.renderer.DocumentElementRenderer


@Composable
fun PrintLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester
) {
    val pageWidth = 595.dp
    val pageMinHeight = 842.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .width(pageWidth)
                .shadow(8.dp)
                .background(Color.White)
                .defaultMinSize(minHeight = pageMinHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 72.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                viewModel.documentElements.forEachIndexed { index, element ->
                    DocumentElementRenderer(element, index, viewModel)
                }
            }
        }
    }
}
