package com.tosin.docprocessor.ui.editor.layouts.print

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.color.DocumentColorTokens
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
    val canvasColor by animateColorAsState(
        targetValue = Color(tokens.canvasBackground),
        label = "print-canvas"
    )

    val currentDocument by viewModel.currentDocument.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val documentData = remember(currentDocument, viewModel.documentElements) {
        currentDocument?.copy(content = viewModel.documentElements)
            ?: DocumentData(content = viewModel.documentElements)
    }

    val pagesState = remember { mutableStateOf<List<PageModel>>(emptyList()) }
    val pages = pagesState.value

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    val zoomAnimatable = remember { Animatable(1f) }

    // Auto-focus zoom to 1.25f when entering edit on an element
    LaunchedEffect(uiState.activeEditingElementId) {
        if (uiState.activeEditingElementId != null && zoomScale < 1.25f) {
            coroutineScope.launch {
                zoomAnimatable.snapTo(zoomScale)
                zoomAnimatable.animateTo(1.25f, animationSpec = tween(300))
                zoomScale = zoomAnimatable.value
                viewModel.setZoomScale(zoomScale)
            }
        }
    }

    // Debounced pagination (300 ms) to avoid thrashing on keystrokes.
    // isDarkMode is a key: baked text colours must be rebuilt when the theme
    // flips or dark-mode pages would keep light-mode (invisible) text.
    LaunchedEffect(documentData, isDarkMode) {
        delay(300)
        val result = withContext(Dispatchers.Default) {
            val sections = documentData.content.filterIsInstance<DocumentElement.Section>()
            val defaultDimensions = if (sections.isNotEmpty()) {
                sectionResolver.resolve(sections.first().properties)
            } else {
                PageDimensions.Default
            }

            paginator.paginate(documentData, defaultDimensions, isDarkMode = isDarkMode)
        }
        // Generate stable keys for pages
        val pagesWithKeys = result.mapIndexed { idx, page ->
            val firstElementId = page.elements.firstOrNull()?.elementId ?: ""
            val contentHash = page.elements.fold(0) { acc, elem -> acc + elem.elementId.hashCode() }
            page.copy(
                stableKey = "${firstElementId}_${idx}",
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
            mostVisibleItem?.index?.plus(1) to pages.size
        }
            .distinctUntilChanged()
            .collect { (currentPage, pageCount) ->
                if (pageCount > 0) {
                    viewModel.onPrintPageChanged(currentPage ?: 1, pageCount)
                }
            }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (zoomScale * zoomChange).coerceIn(0.5f, 2.5f)
        zoomScale = newScale
        viewModel.setZoomScale(newScale)
        panOffset = if (newScale > 1f) {
            val maxPan = 400f * (newScale - 1f)
            Offset(
                x = (panOffset.x + panChange.x).coerceIn(-maxPan, maxPan),
                y = (panOffset.y + panChange.y).coerceIn(-maxPan, maxPan)
            )
        } else {
            Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasColor)
            .pointerInput(isEditable) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!isEditable) {
                            viewModel.enterEditMode()
                            return@detectTapGestures
                        }
                        val target = if (zoomScale > 1.2f) 1f else 1.25f
                        coroutineScope.launch {
                            zoomAnimatable.snapTo(zoomScale)
                            zoomAnimatable.animateTo(target, animationSpec = tween(250))
                            zoomScale = zoomAnimatable.value
                            panOffset = Offset.Zero
                            viewModel.setZoomScale(target)
                        }
                    }
                )
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = panOffset.x
                    translationY = panOffset.y
                },
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
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}
