package com.tosin.docprocessor.ui.editor.layouts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.renderer.DocumentElementRenderer


import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

@Composable
fun MobileLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    isDarkMode: Boolean = false
) {
    val pageBackground = if (isDarkMode) Color(0xFF2B2B2B) else Color.White
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .padding(16.dp)
            .focusRequester(focusRequester)
    ) {
        itemsIndexed(viewModel.documentElements) { index, element ->
            DocumentElementRenderer(
                element = element,
                index = index,
                viewModel = viewModel,
                isEditable = isEditable,
                isDarkMode = isDarkMode
            )
        }
    }
}
