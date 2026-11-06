package com.tosin.docprocessor.ui.editor.layouts

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.tosin.docprocessor.data.local.dao.RecentFileDao
import com.tosin.docprocessor.data.local.entities.RecentFile
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.data.repository.DocumentRepository
import com.tosin.docprocessor.domain.document.ParseResult
import com.tosin.docprocessor.domain.model.DocumentData
import com.tosin.docprocessor.domain.model.DocumentElement
import com.tosin.docprocessor.domain.model.EditorMode
import com.tosin.docprocessor.ui.editor.EditorViewModel
import com.tosin.docprocessor.ui.editor.layouts.print.PrintLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PreviewEditGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mobilePreviewDoubleTapEntersEditModeAndSingleTapDoesNot() {
        val viewModel = createEditorViewModel()

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            MaterialTheme {
                MobileLayout(
                    viewModel = viewModel,
                    focusRequester = remember { FocusRequester() },
                    isEditable = uiState.editorMode == EditorMode.EDIT
                )
            }
        }

        composeTestRule.onRoot().performTouchInput { click() }
        composeTestRule.runOnIdle {
            assertEquals(EditorMode.PREVIEW, viewModel.uiState.value.editorMode)
        }

        composeTestRule.onRoot().performTouchInput { doubleClick() }
        composeTestRule.runOnIdle {
            assertEquals(EditorMode.EDIT, viewModel.uiState.value.editorMode)
        }
    }

    @Test
    fun mobilePreviewStillScrollsWithDoubleTapGestureInstalled() {
        val elements = (1..40).map { number ->
            DocumentElement.Paragraph(spans = listOf(TextSpan("Paragraph $number")))
        }
        val viewModel = createEditorViewModel(elements)

        composeTestRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            MaterialTheme {
                MobileLayout(
                    viewModel = viewModel,
                    focusRequester = remember { FocusRequester() },
                    isEditable = uiState.editorMode == EditorMode.EDIT
                )
            }
        }
        composeTestRule.runOnIdle {
            viewModel.onFilePicked(Uri.parse("content://test/document.docx"))
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.documentElements.size == elements.size
        }

        repeat(4) {
            composeTestRule.onRoot().performTouchInput { swipeUp() }
        }

        composeTestRule.onNodeWithText("Paragraph 40").assertExists()
    }

    @Test
    fun printPreviewDoubleTapUsesCurrentModeInsteadOfStalePointerInputState() {
        val viewModel = createEditorViewModel()
        val isEditable = mutableStateOf(true)
        viewModel.enterEditMode()

        composeTestRule.setContent {
            MaterialTheme {
                PrintLayout(
                    viewModel = viewModel,
                    focusRequester = remember { FocusRequester() },
                    isEditable = isEditable.value
                )
            }
        }

        composeTestRule.runOnIdle {
            isEditable.value = false
            viewModel.toggleEditorMode()
        }

        composeTestRule.onRoot().performTouchInput { doubleClick() }
        composeTestRule.runOnIdle {
            assertEquals(EditorMode.EDIT, viewModel.uiState.value.editorMode)
            assertEquals(1f, viewModel.uiState.value.zoomScale, 0.001f)
        }

        composeTestRule.runOnIdle {
            isEditable.value = true
        }
        composeTestRule.onRoot().performTouchInput { doubleClick() }
        composeTestRule.runOnIdle {
            assertEquals(1.25f, viewModel.uiState.value.zoomScale, 0.001f)
        }
    }

    private fun createEditorViewModel(
        documentContent: List<DocumentElement> = emptyList()
    ) = EditorViewModel(
        documentRepository = object : DocumentRepository {
            override suspend fun readDocumentFromUri(uri: Uri): ParseResult =
                ParseResult(content = documentContent)

            override suspend fun saveDocumentToUri(
                uri: Uri,
                content: List<DocumentElement>
            ) {
                error("Not used in this test")
            }

            override suspend fun getFileName(uri: Uri): String = "document.docx"

            override fun loadDocument(filePath: String): Flow<DocumentData> = emptyFlow()

            override fun saveDocument(document: DocumentData): Flow<Unit> = emptyFlow()

            override suspend fun parseDocument(filePath: String): DocumentData =
                error("Not used in this test")

            override suspend fun createNewDocument(uri: Uri) {
                error("Not used in this test")
            }
        },
        recentFileDao = object : RecentFileDao {
            override fun getRecentFiles(): Flow<List<RecentFile>> = emptyFlow()

            override suspend fun insertFile(file: RecentFile) {
            }

            override suspend fun deleteFile(uri: String) {
                error("Not used in this test")
            }

            override suspend fun pruneOldFiles(): Int = error("Not used in this test")
        }
    )
}
