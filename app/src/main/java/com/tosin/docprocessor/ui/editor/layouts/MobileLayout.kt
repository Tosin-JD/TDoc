package com.tosin.docprocessor.ui.editor.layouts

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.renderer.DocumentElementRenderer

/** A body element plus its index in the full element list (used for edits). */
private data class MobileListItem(
    val index: Int,
    val element: DocumentElement
)

/**
 * Reflowing mobile reading/editing surface.
 *
 * The list shows only [MobileContentPolicy] body content, in document order,
 * wrapped to the available width. Layout control data and metadata records stay
 * out of the list entirely. Horizontal padding adapts to the window width and
 * avoids display cutouts in landscape; the background follows the one resolved
 * app theme and animates when it changes.
 */
@Composable
fun MobileLayout(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean
) {
    val elements = viewModel.documentElements
    val items = remember(elements) {
        elements.withIndex()
            .filter { MobileContentPolicy.isBodyContent(it.value) }
            .map { MobileListItem(index = it.index, element = it.value) }
    }
    val background by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface,
        label = "mobile-background"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
        ) {
            val horizontalPadding = when {
                maxWidth >= 840.dp -> 48.dp
                maxWidth >= 600.dp -> 32.dp
                else -> 20.dp
            }

            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .focusRequester(focusRequester)
                    .pointerInput(isEditable) {
                        if (!isEditable) detectTapGestures(onDoubleTap = { viewModel.enterEditMode() })
                    },
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 12.dp,
                    bottom = 32.dp
                )
            ) {
                items(
                    items = items,
                    key = { item -> item.element.id },
                    contentType = { item -> item.element::class }
                ) { item ->
                    DocumentElementRenderer(
                        element = item.element,
                        index = item.index,
                        viewModel = viewModel,
                        isEditable = isEditable
                    )
                }
            }
        }
    }
}
