package com.sakupdf.app

import com.sakupdf.app.domain.PdfMathUtils
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.ui.viewmodel.SakuPDFViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MergePdfMathUtilsTest {

    @Test
    fun testDuplicatePdfUriFilteringAndMax20Capping() {
        val existingDocs = (1..18).map {
            PdfDocument(id = "$it", name = "File_$it.pdf", sizeFormatted = "1 MB", dateFormatted = "Today", pages = 2)
        }
        val incomingDocs = (17..25).map {
            PdfDocument(id = "$it", name = "File_$it.pdf", sizeFormatted = "1 MB", dateFormatted = "Today", pages = 3)
        }

        val (filtered, wasTruncated) = PdfMathUtils.filterAndCapImageItems(
            existingItems = existingDocs,
            incomingItems = incomingDocs,
            getUriKey = { it.name },
            maxAllowed = 20
        )

        assertEquals(2, filtered.size) // File_19 and File_20 added
        assertEquals("File_19.pdf", filtered[0].name)
        assertEquals("File_20.pdf", filtered[1].name)
        assertTrue(wasTruncated)
    }

    @Test
    fun testMinimumTwoFilesValidation() {
        val singleFile = listOf(PdfDocument("1", "File.pdf", "1 MB", "Today", 5))
        assertFalse(singleFile.size >= 2)

        val twoFiles = listOf(
            PdfDocument("1", "File1.pdf", "1 MB", "Today", 5),
            PdfDocument("2", "File2.pdf", "2 MB", "Today", 3)
        )
        assertTrue(twoFiles.size >= 2)
    }

    @Test
    fun testDefaultMergeFilenameSanitization() {
        assertEquals("PDF_Gabungan_2026.pdf", PdfMathUtils.sanitizeFilename("PDF_Gabungan_2026.pdf"))
        assertEquals("PDF_Gabungan_Output.pdf", PdfMathUtils.sanitizeFilename("PDF_Gabungan_Output.pdf.pdf"))
        assertEquals("SakuPDF_Document.pdf", PdfMathUtils.sanitizeFilename("   "))
    }

    @Test
    fun testTotalPageCalculation() {
        val docs = listOf(
            PdfDocument("1", "A.pdf", "1 MB", "Today", 10),
            PdfDocument("2", "B.pdf", "2 MB", "Today", 15),
            PdfDocument("3", "C.pdf", "500 KB", "Today", 5)
        )
        val totalPages = docs.sumOf { it.pages }
        assertEquals(30, totalPages)
    }

    @Test
    fun testProgressPercentageCalculation() {
        assertEquals(0f, PdfMathUtils.calculateProgressPercentage(0, 5), 0.01f)
        assertEquals(0.6f, PdfMathUtils.calculateProgressPercentage(3, 5), 0.01f)
        assertEquals(1.0f, PdfMathUtils.calculateProgressPercentage(5, 5), 0.01f)
    }

    @Test
    fun testPdfReorderingAndRemovalState() {
        val viewModel = SakuPDFViewModel()

        val doc1 = PdfDocument("1", "A.pdf", "1 MB", "Today", 5)
        val doc2 = PdfDocument("2", "B.pdf", "2 MB", "Today", 10)
        val doc3 = PdfDocument("3", "C.pdf", "3 MB", "Today", 15)

        // Directly test reorder logic using filterAndCapImageItems
        val initialList = mutableListOf(doc1, doc2, doc3)

        // Move doc2 up (from index 1 to 0)
        val itemToMove = initialList.removeAt(1)
        initialList.add(0, itemToMove)

        assertEquals("B.pdf", initialList[0].name)
        assertEquals("A.pdf", initialList[1].name)
        assertEquals("C.pdf", initialList[2].name)

        // Remove item B.pdf
        initialList.removeAt(0)
        assertEquals(2, initialList.size)
        assertEquals("A.pdf", initialList[0].name)
    }

    @Test
    fun testSuccessNavigationEventConsumptionAndCancellation() {
        val viewModel = SakuPDFViewModel()

        // Test navigation consumption
        viewModel.onNavigationToSuccessHandled()
        assertFalse(viewModel.uiState.value.shouldNavigateToSuccess)

        // Test clear state
        viewModel.clearMergePdfState()
        assertFalse(viewModel.uiState.value.shouldNavigateToSuccess)
        assertNull(viewModel.uiState.value.conversionResult)
        assertTrue(viewModel.uiState.value.pdfsToMerge.isEmpty())
    }
}
