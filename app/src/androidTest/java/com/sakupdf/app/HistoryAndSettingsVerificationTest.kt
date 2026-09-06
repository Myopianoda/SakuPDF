package com.sakupdf.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.AppSettingsManager
import com.sakupdf.app.domain.DocumentHistoryManager
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.model.ThemeOption
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryAndSettingsVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Reset history file for clean testing
        val historyFile = File(context.filesDir, "sakupdf_document_history.json")
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }

    @Test
    fun testHistoryIndexingAndPersistence() {
        val doc1 = PdfDocument(
            id = "doc_1",
            name = "TestDocument1.pdf",
            sizeFormatted = "500 KB",
            dateFormatted = "Hari ini",
            pages = 5,
            isPdf = true,
            location = "Downloads",
            status = "Tersimpan",
            uri = Uri.parse("content://media/external/file/101")
        )

        val doc2 = PdfDocument(
            id = "doc_2",
            name = "TestDocument2.pdf",
            sizeFormatted = "1.2 MB",
            dateFormatted = "Hari ini",
            pages = 10,
            isPdf = true,
            location = "Documents",
            status = "Tersimpan",
            uri = Uri.parse("content://media/external/file/102")
        )

        // 1. Add documents
        DocumentHistoryManager.addDocument(context, doc1)
        val updated = DocumentHistoryManager.addDocument(context, doc2)

        assertEquals("History should contain 2 items", 2, updated.size)
        assertEquals("Newest document should be at the top", "doc_2", updated[0].id)
        assertEquals("Previous document should follow", "doc_1", updated[1].id)

        // 2. Reload from fresh disk read
        val loaded = DocumentHistoryManager.loadHistory(context)
        assertEquals("Reloaded history should have 2 items", 2, loaded.size)
        assertEquals("doc_2", loaded[0].id)
        assertEquals("TestDocument2.pdf", loaded[0].name)
        assertEquals("1.2 MB", loaded[0].sizeFormatted)
        assertEquals(10, loaded[0].pages)
        assertEquals(Uri.parse("content://media/external/file/102"), loaded[0].uri)

        // 3. Deduplication on re-add
        val reAdded = DocumentHistoryManager.addDocument(context, doc1)
        assertEquals("Duplicate re-add should maintain count of 2", 2, reAdded.size)
        assertEquals("Re-added doc1 should now be at the top", "doc_1", reAdded[0].id)

        // 4. Remove document
        val afterRemove = DocumentHistoryManager.removeDocument(context, "doc_2")
        assertEquals("Should have 1 item left", 1, afterRemove.size)
        assertEquals("doc_1", afterRemove[0].id)

        val finalLoaded = DocumentHistoryManager.loadHistory(context)
        assertEquals(1, finalLoaded.size)
    }

    @Test
    fun testMissingAndDeletedUriHandling() {
        val tempFile = File(context.cacheDir, "temp_test_${UUID.randomUUID()}.pdf")
        FileOutputStream(tempFile).use { it.write("DUMMY".toByteArray()) }
        val fileUri = Uri.fromFile(tempFile)

        // URI is accessible while file exists
        assertTrue("Real file URI should be accessible", DocumentHistoryManager.isUriAccessible(context.contentResolver, fileUri))

        // Delete file
        tempFile.delete()

        // URI is no longer accessible
        assertFalse("Deleted file URI should not be accessible", DocumentHistoryManager.isUriAccessible(context.contentResolver, fileUri))

        // Null URI
        assertFalse("Null URI should not be accessible", DocumentHistoryManager.isUriAccessible(context.contentResolver, null))

        // Random non-existent content URI should not crash
        val nonExistentContentUri = Uri.parse("content://non.existent.provider/item/99999")
        assertFalse("Non-existent content URI should return false without throwing", DocumentHistoryManager.isUriAccessible(context.contentResolver, nonExistentContentUri))
    }

    @Test
    fun testSafProviderFailureGracefulDegradation() {
        val docWithUnsupportedUri = PdfDocument(
            id = "unsupported_doc",
            name = "LockedProvider.pdf",
            sizeFormatted = "300 KB",
            dateFormatted = "Hari ini",
            pages = 2,
            isPdf = true,
            uri = Uri.parse("content://unsupported.authority/document/123")
        )

        DocumentHistoryManager.addDocument(context, docWithUnsupportedUri)
        assertEquals(1, DocumentHistoryManager.loadHistory(context).size)

        // 1. Delete on unsupported provider should not throw, should return false, and clean local index
        val (safDeleted, remaining) = DocumentHistoryManager.deleteDocumentSaf(
            context = context,
            contentResolver = context.contentResolver,
            doc = docWithUnsupportedUri
        )

        assertFalse("Provider deletion should report false for unsupported authority", safDeleted)
        assertTrue("Local history should be cleaned so user is not blocked", remaining.isEmpty())
        assertTrue("Disk history should be empty", DocumentHistoryManager.loadHistory(context).isEmpty())

        // 2. Rename on unsupported provider should not throw, should gracefully rename in local history
        val docToRename = PdfDocument(
            id = "rename_doc",
            name = "OriginalName.pdf",
            sizeFormatted = "100 KB",
            dateFormatted = "Hari ini",
            pages = 1,
            isPdf = true,
            uri = Uri.parse("content://unsupported.authority/document/456")
        )
        DocumentHistoryManager.addDocument(context, docToRename)

        val (renamedDoc, updatedList) = DocumentHistoryManager.renameDocumentSaf(
            context = context,
            contentResolver = context.contentResolver,
            doc = docToRename,
            newName = "NewSanitizedName"
        )

        assertEquals("NewSanitizedName.pdf", renamedDoc.name)
        assertEquals(1, updatedList.size)
        assertEquals("NewSanitizedName.pdf", updatedList[0].name)
    }

    @Test
    fun testSettingsPersistenceAndStateRestoration() {
        // 1. Set settings
        AppSettingsManager.saveThemeOption(context, ThemeOption.DARK)
        AppSettingsManager.saveDefaultQuality(context, "Tinggi (Disarankan)")
        AppSettingsManager.saveDefaultPageSize(context, "A4")
        AppSettingsManager.saveDefaultMargin(context, "Sedang")
        AppSettingsManager.saveDefaultCompression(context, "Ukuran minimum")

        // 2. Verify reading from settings manager
        assertEquals(ThemeOption.DARK, AppSettingsManager.getThemeOption(context))
        assertEquals("Tinggi (Disarankan)", AppSettingsManager.getDefaultQuality(context))
        assertEquals("A4", AppSettingsManager.getDefaultPageSize(context))
        assertEquals("Sedang", AppSettingsManager.getDefaultMargin(context))
        assertEquals("Ukuran minimum", AppSettingsManager.getDefaultCompression(context))

        // 3. Populate a document in history
        val testDoc = PdfDocument("vm_doc", "RestoredDoc.pdf", "400 KB", "Hari ini", 3)
        DocumentHistoryManager.addDocument(context, testDoc)

        // 4. Create ViewModel and load persisted data
        val viewModel = SakuPDFViewModel()
        viewModel.loadPersistedData(context)

        val state = viewModel.uiState.value
        assertEquals(ThemeOption.DARK, state.themeOption)
        assertEquals("A4", state.pdfSettings.pageSize)
        assertEquals("Sedang", state.pdfSettings.margin)
        assertEquals("Tinggi (Disarankan)", state.pdfSettings.quality)
        assertEquals("RestoredDoc.pdf", state.documents.first().name)
    }
}
