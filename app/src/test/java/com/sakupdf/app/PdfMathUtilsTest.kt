package com.sakupdf.app

import com.sakupdf.app.domain.PdfMathUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMathUtilsTest {

    @Test
    fun testSanitizeFilename() {
        assertEquals("Laporan_2026_Test.pdf", PdfMathUtils.sanitizeFilename("Laporan/2026:Test.pdf"))
        assertEquals("SakuPDF_Document.pdf", PdfMathUtils.sanitizeFilename("   "))
        assertEquals("MyDoc.pdf", PdfMathUtils.sanitizeFilename("MyDoc"))
        assertEquals("MyDoc.pdf", PdfMathUtils.sanitizeFilename("MyDoc.pdf"))
        assertEquals("Doc_Name.pdf", PdfMathUtils.sanitizeFilename("Doc*Name?.pdf"))
        assertEquals("CleanDoc.pdf", PdfMathUtils.sanitizeFilename("CleanDoc.pdf.pdf"))
        assertEquals("NestedExt.pdf", PdfMathUtils.sanitizeFilename("NestedExt.PDF.pdf.PDF"))
        assertEquals("ControlChar.pdf", PdfMathUtils.sanitizeFilename("Control\u0000Char\u001F.pdf"))
    }

    @Test
    fun testDuplicateUriFilteringAndCapping() {
        val existingUris = listOf("uri://1", "uri://2", "uri://3")
        val incomingUris = listOf("uri://2", "uri://4", "uri://4", "uri://5", "uri://6")

        val (filtered, wasTruncated) = PdfMathUtils.filterAndCapImageItems(
            existingItems = existingUris,
            incomingItems = incomingUris,
            getUriKey = { it },
            maxAllowed = 30
        )

        assertEquals(listOf("uri://4", "uri://5", "uri://6"), filtered)
        assertFalse(wasTruncated)
    }

    @Test
    fun testMax30ImagesEnforcement() {
        val existingUris = (1..28).map { "uri://$it" }
        val incomingUris = (29..35).map { "uri://$it" }

        val (filtered, wasTruncated) = PdfMathUtils.filterAndCapImageItems(
            existingItems = existingUris,
            incomingItems = incomingUris,
            getUriKey = { it },
            maxAllowed = 30
        )

        assertEquals(2, filtered.size) // Only 29 and 30 added
        assertEquals(listOf("uri://29", "uri://30"), filtered)
        assertTrue(wasTruncated)
    }

    @Test
    fun testCalculatePageSizePoints() {
        val a4 = PdfMathUtils.calculatePageSizePoints("A4", 1000, 1000)
        assertEquals(595f, a4.first, 0.01f)
        assertEquals(842f, a4.second, 0.01f)

        val letter = PdfMathUtils.calculatePageSizePoints("Letter", 1000, 1000)
        assertEquals(612f, letter.first, 0.01f)
        assertEquals(792f, letter.second, 0.01f)

        val autoLandscape = PdfMathUtils.calculatePageSizePoints("Otomatis", 1600, 1200)
        assertEquals(842f, autoLandscape.first, 0.01f)
        assertEquals(631.5f, autoLandscape.second, 0.5f)

        val autoPortrait = PdfMathUtils.calculatePageSizePoints("Otomatis", 1200, 1600)
        assertEquals(631.5f, autoPortrait.first, 0.5f)
        assertEquals(842f, autoPortrait.second, 0.01f)
    }

    @Test
    fun testApplyOrientation() {
        val landscapePage = Pair(842f, 595f)
        val forcedPortrait = PdfMathUtils.applyOrientation(landscapePage, "Potret")
        assertEquals(595f, forcedPortrait.first, 0.01f)
        assertEquals(842f, forcedPortrait.second, 0.01f)

        val portraitPage = Pair(595f, 842f)
        val forcedLandscape = PdfMathUtils.applyOrientation(portraitPage, "Lanskap")
        assertEquals(842f, forcedLandscape.first, 0.01f)
        assertEquals(595f, forcedLandscape.second, 0.01f)
    }

    @Test
    fun testCalculateMarginPoints() {
        assertEquals(0f, PdfMathUtils.calculateMarginPoints("Tanpa margin"), 0.01f)
        assertEquals(24f, PdfMathUtils.calculateMarginPoints("Kecil"), 0.01f)
        assertEquals(48f, PdfMathUtils.calculateMarginPoints("Sedang"), 0.01f)
    }

    @Test
    fun testCalculateMaxDimension() {
        assertEquals(1600, PdfMathUtils.calculateMaxDimension("Hemat ruang"))
        assertEquals(2400, PdfMathUtils.calculateMaxDimension("Seimbang (Default)"))
        assertEquals(3200, PdfMathUtils.calculateMaxDimension("Tinggi"))
    }

    @Test
    fun testCalculateProgressPercentage() {
        assertEquals(0.0f, PdfMathUtils.calculateProgressPercentage(0, 10), 0.01f)
        assertEquals(0.5f, PdfMathUtils.calculateProgressPercentage(5, 10), 0.01f)
        assertEquals(1.0f, PdfMathUtils.calculateProgressPercentage(10, 10), 0.01f)
        assertEquals(0.0f, PdfMathUtils.calculateProgressPercentage(0, 0), 0.01f)
    }
}
