package com.tosin.docprocessor.ui.editor.layouts.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tosin.docprocessor.core.rendering.PrintElementRenderer
import com.tosin.docprocessor.data.common.model.DocumentData
import com.tosin.docprocessor.data.common.model.DocumentElement
import com.tosin.docprocessor.data.common.model.layout.PageDimensions
import com.tosin.docprocessor.data.common.model.layout.PageModel
import com.tosin.docprocessor.data.parser.internal.models.ParagraphAlignment
import com.tosin.docprocessor.domain.pagination.FlowController
import com.tosin.docprocessor.domain.pagination.LayoutRegistry
import com.tosin.docprocessor.domain.pagination.PrintPaginator
import com.tosin.docprocessor.domain.pagination.UnitConverter
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.interaction.CanvasEditOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val MOBILE_MARGIN_HORIZONTAL_DP = 16
private const val MOBILE_MARGIN_VERTICAL_DP = 20
private const val MOBILE_PAGE_PADDING_DP = 8
private const val MOBILE_PAGE_MIN_HEIGHT_PT = 240f
private const val MOBILE_PAGE_MAX_HEIGHT_PT = 20_000f
private const val MOBILE_LOGICAL_PAGE_WIDTH_PT = 520f

@Composable
fun MobileLayoutRenderer(
    viewModel: EditorViewModel,
    focusRequester: FocusRequester,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val unitConverter = remember(context) { UnitConverter(context) }
    val textMeasurer = remember(context, unitConverter) {
        com.tosin.docprocessor.domain.pagination.TextMeasurer(context, unitConverter)
    }
    val layoutRegistry = remember { LayoutRegistry() }
    val flowController = remember(textMeasurer, unitConverter) { FlowController(textMeasurer, unitConverter) }
    val paginator = remember(textMeasurer, unitConverter, layoutRegistry, flowController) {
        PrintPaginator(textMeasurer, unitConverter, layoutRegistry, flowController)
    }
    val renderer = remember(unitConverter) { PrintElementRenderer(unitConverter) }

    val currentDocument by viewModel.currentDocument.collectAsState()
    val documentData = remember(currentDocument, viewModel.documentElements) {
        currentDocument?.copy(content = viewModel.documentElements)
            ?: DocumentData(content = viewModel.documentElements)
    }

    val viewportWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val horizontalPaddingPx = with(density) { (MOBILE_MARGIN_HORIZONTAL_DP.dp * 2 + MOBILE_PAGE_PADDING_DP.dp * 2).toPx() }
    val availableCanvasWidthPx = (viewportWidthPx - horizontalPaddingPx).coerceAtLeast(1f)
    val mobilePageDimensions = remember { createMobilePageDimensions() }

    val pageModel by produceState(
        initialValue = PageModel(index = 0, dimensions = mobilePageDimensions, elements = emptyList()),
        key1 = documentData,
        key2 = mobilePageDimensions,
        key3 = paginator
    ) {
        value = withContext(Dispatchers.Default) {
            paginator.paginate(documentData, mobilePageDimensions).firstOrNull()
                ?.withResolvedHeight()
                ?: PageModel(index = 0, dimensions = mobilePageDimensions, elements = emptyList()).withResolvedHeight()
        }
    }

    val basePageWidthPx = remember(pageModel.dimensions.width, unitConverter) {
        unitConverter.ptToPx(pageModel.dimensions.width)
    }
    val pageScale = remember(availableCanvasWidthPx, basePageWidthPx) {
        if (basePageWidthPx > 0f) (availableCanvasWidthPx / basePageWidthPx).coerceAtMost(1f) else 1f
    }
    val scaledPageHeightDp = with(density) {
        (unitConverter.ptToPx(pageModel.dimensions.height) * pageScale).toDp()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .verticalScroll(scrollState)
            .padding(horizontal = MOBILE_MARGIN_HORIZONTAL_DP.dp, vertical = MOBILE_MARGIN_VERTICAL_DP.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledPageHeightDp)
                .background(Color.White)
                .padding(MOBILE_PAGE_PADDING_DP.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledPageHeightDp)
            ) {
                with(renderer) {
                    if (pageScale != 1f) {
                        withTransform({
                            scale(scaleX = pageScale, scaleY = pageScale, pivot = Offset.Zero)
                        }) {
                            pageModel.elements.forEach { render(it, renderTextContent = true) }
                        }
                    } else {
                        pageModel.elements.forEach { render(it, renderTextContent = true) }
                    }
                }
            }

            if (isEditable) {
                CanvasEditOverlay(
                    pageElements = pageModel.elements,
                    pageScale = pageScale,
                    unitConverter = unitConverter,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun createMobilePageDimensions(): PageDimensions {
    val horizontalMargin = 28f
    val verticalMargin = 32f
    return PageDimensions(
        width = MOBILE_LOGICAL_PAGE_WIDTH_PT,
        height = MOBILE_PAGE_MAX_HEIGHT_PT,
        marginLeft = horizontalMargin,
        marginTop = verticalMargin,
        marginRight = horizontalMargin,
        marginBottom = verticalMargin
    )
}

private fun PageModel.withResolvedHeight(): PageModel {
    val contentBottom = elements.maxOfOrNull { it.bounds.bottom } ?: dimensions.marginTop
    val resolvedHeight = max(MOBILE_PAGE_MIN_HEIGHT_PT, contentBottom + dimensions.marginBottom)
    return copy(dimensions = dimensions.copy(height = resolvedHeight))
}

private fun DocumentElement.Paragraph.overlayTextStyle(): TextStyle {
    val primarySpan = spans.firstOrNull()
    return TextStyle(
        fontSize = ((primarySpan?.fontSize ?: 12) * 1.15f).sp,
        lineHeight = ((primarySpan?.fontSize ?: 12) * 1.3f).sp,
        color = Color.Black,
        textAlign = style.alignment.toComposeTextAlign()
    )
}

private fun ParagraphAlignment.toComposeTextAlign(): TextAlign =
    when (this) {
        ParagraphAlignment.END -> TextAlign.End
        ParagraphAlignment.CENTER -> TextAlign.Center
        ParagraphAlignment.JUSTIFIED -> TextAlign.Justify
        ParagraphAlignment.DISTRIBUTED -> TextAlign.Justify
        else -> TextAlign.Start
    }
