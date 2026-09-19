package com.tosin.docprocessor.ui.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.tosin.docprocessor.MainDispatcherRule
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.data.parser.internal.models.TextSpan
import com.tosin.docprocessor.domain.editor.Selection
import com.tosin.docprocessor.model.isTextBlock
import com.tosin.docprocessor.ui.navigation.TDocRoutes
import com.tosin.docprocessor.FakeDocumentRepository
import com.tosin.docprocessor.FakeRecentFilesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val documentRepository = FakeDocumentRepository()
    private val recentFilesRepository = FakeRecentFilesRepository()

    private fun viewModel(
        uri: String? = "content://authority/doc/doc.txt",
        mime: String = MimeTypes.TXT,
        openMode: String = "edit"
    ): EditorViewModel {
        val handle = SavedStateHandle()
        if (uri != null) handle[TDocRoutes.ARG_URI] = uri
        handle[TDocRoutes.ARG_MIME] = mime
        handle[TDocRoutes.ARG_OPEN_MODE] = openMode
        return EditorViewModel(context, documentRepository, recentFilesRepository, handle)
    }

    @Before
    fun setUp() {
        documentRepository.readError = null
        documentRepository.metaError = null
        documentRepository.saveError = null
        recentFilesRepository.recorded.clear()
    }

    @Test
    fun `loads document and records it in recents`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.loadError)
        assertEquals("doc.txt", state.fileName)
        assertEquals("Hello document", state.documentBlocks.first { it.type.isTextBlock }.text)
        assertEquals(1, recentFilesRepository.recorded.size)
    }

    @Test
    fun `load failure surfaces loadError and retry succeeds`() = runTest(mainDispatcherRule.testDispatcher) {
        documentRepository.readError = IllegalStateException("boom while reading")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadError!!.contains("boom while reading"))

        documentRepository.readError = null
        vm.retryLoad()
        advanceUntilIdle()
        assertNull(vm.uiState.value.loadError)
        assertEquals("Hello document", vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.text)
    }

    @Test
    fun `editing makes document dirty and undo redo restore clean history`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val initialState = vm.uiState.value.documentBlocks
        val blockId = initialState.first { it.type.isTextBlock }.id

        vm.onTextChange(blockId, listOf(TextSpan(text = "Edited text")))
        val afterEdit = vm.uiState.value
        assertTrue(afterEdit.isDirty)
        assertEquals("Edited text", afterEdit.documentBlocks.first { it.type.isTextBlock }.text)
        assertTrue(afterEdit.canUndo)

        vm.undo()
        assertEquals("Hello document", vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.text)

        vm.redo()
        assertEquals("Edited text", vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.text)
    }

    @Test
    fun `toggle format reflects in state and redo history`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val blockId = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.id

        vm.onSelectionChange(blockId, Selection(0, 5))
        vm.toggleFormat(com.tosin.docprocessor.domain.editor.SpanProperty.BOLD)

        val spans = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.spans
        assertTrue(spans.sumOf { if (it.isBold) 1 else 0 } > 0)
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun `insert paragraph focuses the new block`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val blockId = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.id

        vm.insertParagraphAfter(blockId)
        val state = vm.uiState.value
        assertEquals(2, state.documentBlocks.count { it.type.isTextBlock })
        assertEquals(state.documentBlocks[1].id, state.selectedBlockId)
        assertEquals(state.documentBlocks[1].id, state.focusRequestBlockId)
        assertTrue(state.isDirty)
    }

    @Test
    fun `fresh document without uri starts with one editable paragraph`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(uri = null)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Untitled", state.fileName)
        assertEquals(1, state.documentBlocks.size)
        assertTrue(state.documentBlocks.single().type.isTextBlock)
        assertFalse(state.isDirty)
        assertFalse(state.isLoading)
    }

    @Test
    fun `save in edit mode writes back and finishes`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(openMode = "edit")
        advanceUntilIdle()
        val blockId = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.id
        vm.onTextChange(blockId, listOf(TextSpan(text = "saved content")))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDirty)

        vm.save()
        advanceUntilIdle()

        assertTrue(documentRepository.savedUris.contains("content://authority/doc/doc.txt"))
        assertFalse(vm.uiState.value.isDirty)
        assertTrue(vm.uiState.value.finished)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `save failure keeps dirty and reports message`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(openMode = "edit")
        advanceUntilIdle()
        val blockId = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.id
        vm.onTextChange(blockId, listOf(TextSpan(text = "content")))
        advanceUntilIdle()
        documentRepository.saveError = IllegalStateException("disk full")

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isDirty)
        assertFalse(vm.uiState.value.finished)
        assertTrue(vm.uiState.value.message!!.contains("disk full"))
    }

    @Test
    fun `view mode save triggers save as request`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(openMode = "view")
        advanceUntilIdle()
        vm.save()
        val request = vm.uiState.value.saveAsRequest
        assertEquals("doc.txt", request?.suggestedName)
        assertEquals(MimeTypes.TXT, request?.mimeType)

        vm.onSaveAsLaunched()
        assertNull(vm.uiState.value.saveAsRequest)
    }

    @Test
    fun `back press on clean document finishes directly`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBackPressed()
        assertTrue(vm.uiState.value.finished)
        assertFalse(vm.uiState.value.showDiscardDialog)
    }

    @Test
    fun `back press on dirty document opens discard dialog`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val blockId = vm.uiState.value.documentBlocks.first { it.type.isTextBlock }.id
        vm.onTextChange(blockId, listOf(TextSpan(text = "changed")))
        advanceUntilIdle()

        vm.onBackPressed()
        assertTrue(vm.uiState.value.showDiscardDialog)
        assertFalse(vm.uiState.value.finished)

        vm.discardAndExit()
        assertFalse(vm.uiState.value.showDiscardDialog)
        assertTrue(vm.uiState.value.finished)
    }
}