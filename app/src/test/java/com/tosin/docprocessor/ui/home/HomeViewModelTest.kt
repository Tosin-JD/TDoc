package com.tosin.docprocessor.ui.home

import android.net.Uri
import com.tosin.docprocessor.FakeDocumentRepository
import com.tosin.docprocessor.FakeRecentFilesRepository
import com.tosin.docprocessor.MainDispatcherRule
import com.tosin.docprocessor.data.common.model.MimeTypes
import com.tosin.docprocessor.model.DocumentMeta
import com.tosin.docprocessor.ui.editor.OpenMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recentFilesRepository = FakeRecentFilesRepository()
    private val documentRepository = FakeDocumentRepository()

    @Before
    fun setUp() {
        recentFilesRepository.recents.value = emptyList()
        recentFilesRepository.observeError = null
        recentFilesRepository.recorded.clear()
        recentFilesRepository.removed.clear()
        documentRepository.readError = null
        documentRepository.metaError = null
    }

    private fun meta(
        uri: String = "content://authority/doc/doc.txt",
        name: String = "doc.txt",
        mime: String = MimeTypes.TXT
    ) = DocumentMeta(uri = uri, fileName = name, mimeType = mime)

    @Test
    fun `starts in loading then emits recents`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        assertEquals(HomeUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        val loaded = vm.uiState.value as HomeUiState.Loaded
        assertEquals(emptyList<DocumentMeta>(), loaded.recents)
    }

    @Test
    fun `stream errors surface as Error state`() = runTest(mainDispatcherRule.testDispatcher) {
        recentFilesRepository.observeError = IllegalStateException("recents gone")
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertTrue((state as HomeUiState.Error).message.contains("recents gone"))
    }

    @Test
    fun `retry after error recollects and recovers`() = runTest(mainDispatcherRule.testDispatcher) {
        recentFilesRepository.observeError = IllegalStateException("boom")
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Error)

        recentFilesRepository.observeError = null
        val recovered = meta()
        recentFilesRepository.recents.value = listOf(recovered)
        vm.startCollectingRecents()
        advanceUntilIdle()

        val loaded = vm.uiState.value as HomeUiState.Loaded
        assertEquals(listOf(recovered), loaded.recents)
    }

    @Test
    fun `openRecent records and navigates to edit`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()
        val doc = meta(name = "notes.txt")
        vm.openRecent(doc)
        advanceUntilIdle()

        assertEquals(listOf(doc), recentFilesRepository.recorded)
        val navigation = vm.navigation.value
        assertEquals(doc.uri, navigation?.uri)
        assertEquals(OpenMode.EDIT, navigation?.openMode)
    }

    @Test
    fun `removeRecent calls repository and clears navigation state stays put`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()
        val doc = meta(uri = "content://authority/doc/gone.txt")
        vm.removeRecent(doc)
        advanceUntilIdle()

        assertEquals(listOf("content://authority/doc/gone.txt"), recentFilesRepository.removed)
        assertNull(vm.navigation.value)
    }

    @Test
    fun `onFilePicked navs for supported mime and records`() = runTest(mainDispatcherRule.testDispatcher) {
        documentRepository.meta = meta(mime = MimeTypes.DOCX, name = "report.docx")
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()

        vm.onFilePicked(Uri.parse("content://authority/doc/report.docx"))
        advanceUntilIdle()

        assertEquals(1, recentFilesRepository.recorded.size)
        val navigation = vm.navigation.value
        assertEquals("report.docx", navigation?.saveAsName)
        assertEquals(OpenMode.VIEW, navigation?.openMode)
    }

    @Test
    fun `onFilePicked rejects unsupported mime with message`() = runTest(mainDispatcherRule.testDispatcher) {
        documentRepository.meta = meta(name = "photo.jpg", mime = "image/jpeg")
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()

        vm.onFilePicked(Uri.parse("content://authority/doc/photo.jpg"))
        advanceUntilIdle()

        assertNull(vm.navigation.value)
        assertTrue(vm.message.value!!.contains("not supported"))
        assertTrue(recentFilesRepository.recorded.isEmpty())
    }

    @Test
    fun `onFilePicked failure reports message`() = runTest(mainDispatcherRule.testDispatcher) {
        documentRepository.metaError = IllegalStateException("cannot query")
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()

        vm.onFilePicked(Uri.parse("content://authority/doc/other.txt"))
        advanceUntilIdle()

        assertNull(vm.navigation.value)
        assertTrue(vm.message.value!!.contains("cannot query"))
    }

    @Test
    fun `onFileCreated creates, records and navigates to edit`() = runTest(mainDispatcherRule.testDispatcher) {
        documentRepository.meta = meta(name = "untitled.docx", mime = MimeTypes.DOCX)
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()

        val newUri = Uri.parse("content://authority/doc/untitled.docx")
        vm.onFileCreated(newUri, "untitled.docx")
        advanceUntilIdle()

        assertTrue(documentRepository.createdUris.contains(newUri.toString()))
        assertEquals(1, recentFilesRepository.recorded.size)
        assertEquals(OpenMode.EDIT, vm.navigation.value?.openMode)
        assertEquals("untitled.docx", vm.navigation.value?.saveAsName)
    }

    @Test
    fun `onNavigationHandled clears navigation`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = HomeViewModel(recentFilesRepository, documentRepository)
        advanceUntilIdle()
        vm.openRecent(meta())
        advanceUntilIdle()
        assertTrue(vm.navigation.value != null)

        vm.onNavigationHandled()
        assertNull(vm.navigation.value)
    }
}