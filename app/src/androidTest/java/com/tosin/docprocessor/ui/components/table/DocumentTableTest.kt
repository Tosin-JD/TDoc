package com.tosin.docprocessor.ui.components.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.tosin.docprocessor.ui.components.DocumentTable
import com.tosin.docprocessor.ui.components.table.model.TableCellUi
import com.tosin.docprocessor.ui.components.table.model.TableRowUi
import com.tosin.docprocessor.ui.components.table.model.TableUiModel
import org.junit.Rule
import org.junit.Test

class DocumentTableTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun model(vararg rows: List<String>, header: Boolean = false): TableUiModel {
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        return TableUiModel(
            columnCount = columnCount,
            rows = rows.mapIndexed { index, cells ->
                TableRowUi(
                    cells = cells.map { TableCellUi(it) },
                    isHeader = header && index == 0
                )
            },
            columnWeights = List(columnCount) { 1f }
        )
    }

    @Test
    fun everyCellIsRendered() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(800.dp)) {
                DocumentTable(model = model(listOf("A1", "B1"), listOf("A2", "B2")))
            }
        }

        listOf("A1", "B1", "A2", "B2").forEach { text ->
            composeTestRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun emptyTableRendersNothing() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(800.dp)) {
                DocumentTable(model = model())
            }
        }

        composeTestRule.onNodeWithTag("table-scroll").assertDoesNotExist()
    }

    @Test
    fun narrowTableIsHorizontallyScrollable() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(200.dp)) {
                DocumentTable(
                    model = model(listOf("Column One", "Column Two", "Column Three", "Column Four"))
                )
            }
        }

        composeTestRule
            .onNodeWithTag("table-scroll")
            .assertExists()
            .performTouchInput { swipeLeft() }

        composeTestRule.onNodeWithText("Column Four").assertExists()
    }
}
