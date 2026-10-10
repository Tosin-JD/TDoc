package com.tosin.docprocessor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.core.color.DocumentColorTokens
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.ui.components.table.TableColumnSizer
import com.tosin.docprocessor.ui.components.table.TableLayoutConfig
import com.tosin.docprocessor.ui.components.table.TableUiMapper
import com.tosin.docprocessor.ui.components.table.model.CellTextAlignment
import com.tosin.docprocessor.ui.components.table.model.TableRowUi
import com.tosin.docprocessor.ui.components.table.model.TableUiModel

/**
 * Convenience overload for callers that still hold a [DocumentElement.Table].
 * The mapping/normalization is delegated to [TableUiMapper].
 */
@Composable
fun TableWidget(table: DocumentElement.Table, isDarkMode: Boolean = false) {
    val mapper = remember { TableUiMapper() }
    val model = remember(table) { mapper.map(table) }
    DocumentTable(model = model, isDarkMode = isDarkMode)
}

/**
 * Responsive, Bootstrap/Tailwind-styled table.
 *
 * - Fills the available width when it fits (large screens).
 * - Keeps a minimum readable column width and becomes horizontally scrollable
 *   (right-to-left swipe) when the columns overflow (small screens).
 *
 * The composable is a dumb view over an immutable [TableUiModel]; all layout
 * math lives in [TableColumnSizer].
 */
@Composable
fun DocumentTable(
    model: TableUiModel,
    modifier: Modifier = Modifier,
    config: TableLayoutConfig = TableLayoutConfig.Default,
    isDarkMode: Boolean = false
) {
    if (model.isEmpty) return

    val tokens = if (isDarkMode) DocumentColorTokens.Dark else DocumentColorTokens.Light
    val scrollState = rememberScrollState()
    val shape = RoundedCornerShape(config.cornerRadiusDp.dp)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val layout = remember(model, maxWidth, config) {
            TableColumnSizer.compute(
                columnWeights = model.columnWeights,
                availableWidthDp = maxWidth.value,
                config = config
            )
        }
        val tableWidth = if (layout.requiresScroll) layout.totalWidthDp.dp else maxWidth

        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .testTag("table-scroll")
        ) {
            Column(
                modifier = Modifier
                    .width(tableWidth)
                    .clip(shape)
                    .background(Color(tokens.tableBackground))
                    .border(config.borderWidthDp.dp, Color(tokens.tableBorder), shape)
            ) {
                model.rows.forEachIndexed { rowIndex, row ->
                    TableRowView(
                        row = row,
                        columnWidthsDp = layout.columnWidthsDp,
                        rowIndex = rowIndex,
                        config = config,
                        tokens = tokens
                    )
                    if (rowIndex != model.rows.lastIndex) {
                        HorizontalDivider(
                            thickness = config.borderWidthDp.dp,
                            color = Color(tokens.tableCellBorder)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRowView(
    row: TableRowUi,
    columnWidthsDp: List<Float>,
    rowIndex: Int,
    config: TableLayoutConfig,
    tokens: DocumentColorTokens
) {
    val background = when {
        row.isHeader -> Color(tokens.tableHeaderBackground)
        config.zebraStriping && rowIndex % 2 == 1 -> Color(tokens.tableZebraBackground)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .height(IntrinsicSize.Min)
    ) {
        row.cells.forEachIndexed { cellIndex, cell ->
            Box(
                modifier = Modifier
                    .width(columnWidthsDp.getOrElse(cellIndex) { config.minColumnWidthDp }.dp)
                    .padding(
                        horizontal = config.cellPaddingHorizontalDp.dp,
                        vertical = config.cellPaddingVerticalDp.dp
                    ),
                contentAlignment = cell.alignment.toAlignment()
            ) {
                Text(
                    text = cell.text,
                    style = if (row.isHeader) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isHeader) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (row.isHeader) Color(tokens.tableHeaderText)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (cellIndex != row.cells.lastIndex) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = config.borderWidthDp.dp,
                    color = Color(tokens.tableCellBorder)
                )
            }
        }
    }
}

private fun CellTextAlignment.toAlignment(): Alignment = when (this) {
    CellTextAlignment.START -> Alignment.CenterStart
    CellTextAlignment.CENTER -> Alignment.Center
    CellTextAlignment.END -> Alignment.CenterEnd
}
