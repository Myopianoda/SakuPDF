package com.sakupdf.app

import com.sakupdf.app.domain.PdfMathUtils
import com.sakupdf.app.model.CompressionLevel
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.model.ThemeOption
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel
import org.junit.Assert.*
import org.junit.Test

class HistoryAndSettingsTest {

    @Test
    fun testThemeOptions() {
        assertEquals("Ikuti sistem", ThemeOption.SYSTEM.label)
        assertEquals("Terang", ThemeOption.LIGHT.label)
        assertEquals("Gelap", ThemeOption.DARK.label)
        assertEquals(3, ThemeOption.entries.size)
    }

    @Test
    fun testCompressionLevelDefaults() {
        assertEquals("Kualitas tinggi", CompressionLevel.HIGH.title)
        assertEquals("Seimbang", CompressionLevel.BALANCED.title)
        assertEquals("Ukuran minimum", CompressionLevel.MINIMUM.title)
        assertEquals("Disarankan", CompressionLevel.BALANCED.badge)
    }

    @Test
    fun testViewModelThemeSetting() {
        val vm = SakuPDFViewModel()
        assertEquals(ThemeOption.SYSTEM, vm.uiState.value.themeOption)

        vm.setThemeOption(ThemeOption.DARK)
        assertEquals(ThemeOption.DARK, vm.uiState.value.themeOption)

        vm.setThemeOption(ThemeOption.LIGHT)
        assertEquals(ThemeOption.LIGHT, vm.uiState.value.themeOption)
    }

    @Test
    fun testViewModelSearchAndSort() {
        val vm = SakuPDFViewModel()
        assertEquals("", vm.uiState.value.searchQuery)
        assertEquals("DATE_DESC", vm.uiState.value.sortOrder)

        vm.setSearchQuery("Invoice")
        assertEquals("Invoice", vm.uiState.value.searchQuery)

        vm.toggleSortOrder()
        assertEquals("NAME_ASC", vm.uiState.value.sortOrder)

        vm.toggleSortOrder()
        assertEquals("DATE_ASC", vm.uiState.value.sortOrder)

        vm.toggleSortOrder()
        assertEquals("DATE_DESC", vm.uiState.value.sortOrder)
    }

    @Test
    fun testRenameCandidateWorkflow() {
        val vm = SakuPDFViewModel()
        val doc = PdfDocument("test_1", "DokumenLama.pdf", "100 KB", "Hari ini", 1)

        vm.requestRename(doc)
        assertEquals(doc, vm.uiState.value.renameCandidate)

        vm.dismissRename()
        assertNull(vm.uiState.value.renameCandidate)

        vm.requestRename(doc)
        vm.confirmRename("DokumenBaru")
        assertNull(vm.uiState.value.renameCandidate)
    }

    @Test
    fun testDeleteCandidateWorkflow() {
        val vm = SakuPDFViewModel()
        val doc = PdfDocument("test_del", "ToDelete.pdf", "50 KB", "Hari ini", 1)

        vm.requestDelete(doc)
        assertEquals(doc, vm.uiState.value.deleteCandidate)

        vm.dismissDelete()
        assertNull(vm.uiState.value.deleteCandidate)
    }

    @Test
    fun testFilenameSanitizationForRename() {
        val sanitizedPdf = PdfMathUtils.sanitizeFilename("Laporan Keuangan")
        assertEquals("Laporan Keuangan.pdf", sanitizedPdf)

        val sanitizedPdfWithExt = PdfMathUtils.sanitizeFilename("Dokumen/Penting?.pdf")
        assertEquals("Dokumen_Penting.pdf", sanitizedPdfWithExt)
    }
}
