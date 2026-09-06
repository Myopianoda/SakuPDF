package com.sakupdf.app

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.SplitPdfConverter
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.SplitMethod
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SplitPdfVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createDeterministicMultiPagePdf(file: File, pageCount: Int) {
        val doc = PDDocument()
        try {
            for (i in 1..pageCount) {
                val page = PDPage()
                doc.addPage(page)
                val contentStream = PDPageContentStream(doc, page)
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                contentStream.setNonStrokingColor(0, 50, 150)
                contentStream.newLineAtOffset(50f, 700f)
                contentStream.showText("SakuPDF Marker Page $i")
                contentStream.endText()
                contentStream.close()
            }
            doc.save(file)
        } finally {
            doc.close()
        }
    }

    @Test
    fun testSplitModeAllExtractsEveryPageAsSeparatePdf() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "split_src_all_${UUID.randomUUID()}.pdf")
            createDeterministicMultiPagePdf(sourceFile, 5)
            val sourceBytes = sourceFile.length()
            val outputDir = File(context.cacheDir, "split_out_all_${UUID.randomUUID()}")
            outputDir.mkdirs()

            val progressReports = mutableListOf<ConversionProgress>()

            val result = SplitPdfConverter.split(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "DokumenLaporan.pdf",
                splitMethod = SplitMethod.ALL,
                destinationTreeUri = Uri.fromFile(outputDir),
                onProgress = { progressReports.add(it) }
            )

            assertTrue("Split must succeed", result.isSuccess)
            val splitResult = result.getOrNull()
            assertNotNull(splitResult)
            assertEquals(5, splitResult!!.outputFilesCount)
            assertEquals(5, splitResult.outputUris.size)
            assertTrue("Progress reports should be sent", progressReports.isNotEmpty())

            // Verify each file individually
            var accumulatedOutputBytes = 0L
            for (p in 1..5) {
                val expectedName = String.format("DokumenLaporan_page_%03d.pdf", p)
                val outFile = File(outputDir, expectedName)
                assertTrue("File $expectedName must exist", outFile.exists())
                assertTrue("File size must be > 0", outFile.length() > 0)
                accumulatedOutputBytes += outFile.length()

                // Inspect with PDFBox
                val pdDoc = PDDocument.load(outFile)
                assertEquals("Each split page must have exactly 1 page", 1, pdDoc.numberOfPages)

                // Verify text content is preserved without rasterization
                val stripper = PDFTextStripper()
                val text = stripper.getText(pdDoc)
                assertTrue("Page $p text marker must be preserved", text.contains("SakuPDF Marker Page $p"))
                pdDoc.close()
            }

            println("=== SPLIT ALL AUDIT ===")
            println("SOURCE BYTES: $sourceBytes")
            println("TOTAL OUTPUT BYTES: $accumulatedOutputBytes")
            println("RATIO: ${accumulatedOutputBytes.toFloat() / sourceBytes.toFloat()}")
            println("=======================")

            // Cleanup
            sourceFile.delete()
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun testSplitModeCustomRangePreservesSubrangesAndPages() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "split_src_custom_${UUID.randomUUID()}.pdf")
            createDeterministicMultiPagePdf(sourceFile, 6)
            val sourceBytes = sourceFile.length()
            val outputDir = File(context.cacheDir, "split_out_custom_${UUID.randomUUID()}")
            outputDir.mkdirs()

            val result = SplitPdfConverter.split(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "Invoice.pdf",
                splitMethod = SplitMethod.CUSTOM,
                customRangeString = "1-2, 4-6",
                destinationTreeUri = Uri.fromFile(outputDir),
                onProgress = {}
            )

            assertTrue(result.isSuccess)
            val splitResult = result.getOrNull()
            assertNotNull(splitResult)
            assertEquals(2, splitResult!!.outputFilesCount)

            // File 1: pages 1-2
            val f1 = File(outputDir, "Invoice_pages_1-2.pdf")
            assertTrue(f1.exists())
            val doc1 = PDDocument.load(f1)
            assertEquals(2, doc1.numberOfPages)
            val text1 = PDFTextStripper().getText(doc1)
            assertTrue(text1.contains("SakuPDF Marker Page 1"))
            assertTrue(text1.contains("SakuPDF Marker Page 2"))
            doc1.close()

            // File 2: pages 4-6
            val f2 = File(outputDir, "Invoice_pages_4-6.pdf")
            assertTrue(f2.exists())
            val doc2 = PDDocument.load(f2)
            assertEquals(3, doc2.numberOfPages)
            val text2 = PDFTextStripper().getText(doc2)
            assertTrue(text2.contains("SakuPDF Marker Page 4"))
            assertTrue(text2.contains("SakuPDF Marker Page 5"))
            assertTrue(text2.contains("SakuPDF Marker Page 6"))
            doc2.close()

            sourceFile.delete()
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun testSplitModeVisualSelection() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "split_src_visual_${UUID.randomUUID()}.pdf")
            createDeterministicMultiPagePdf(sourceFile, 4)
            val outputDir = File(context.cacheDir, "split_out_visual_${UUID.randomUUID()}")
            outputDir.mkdirs()

            val result = SplitPdfConverter.split(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "Data.pdf",
                splitMethod = SplitMethod.VISUAL,
                selectedVisualPages = listOf(1, 3),
                destinationTreeUri = Uri.fromFile(outputDir),
                onProgress = {}
            )

            assertTrue(result.isSuccess)
            val splitResult = result.getOrNull()
            assertNotNull(splitResult)
            assertEquals(2, splitResult!!.outputFilesCount)

            val p1 = File(outputDir, "Data_page_001.pdf")
            assertTrue(p1.exists())
            val doc1 = PDDocument.load(p1)
            assertEquals(1, doc1.numberOfPages)
            assertTrue(PDFTextStripper().getText(doc1).contains("SakuPDF Marker Page 1"))
            doc1.close()

            val p3 = File(outputDir, "Data_page_003.pdf")
            assertTrue(p3.exists())
            val doc2 = PDDocument.load(p3)
            assertEquals(1, doc2.numberOfPages)
            assertTrue(PDFTextStripper().getText(doc2).contains("SakuPDF Marker Page 3"))
            doc2.close()

            sourceFile.delete()
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun testSplitRejectsCorruptFile() {
        runBlocking {
            val corruptFile = File(context.cacheDir, "corrupt_${UUID.randomUUID()}.pdf")
            FileOutputStream(corruptFile).use { it.write("NOT_A_PDF_CONTENT".toByteArray()) }

            val result = SplitPdfConverter.split(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(corruptFile),
                sourceName = "corrupt.pdf",
                splitMethod = SplitMethod.ALL,
                destinationTreeUri = Uri.fromFile(context.cacheDir),
                onProgress = {}
            )

            assertTrue("Should fail on corrupt file", result.isFailure)
            corruptFile.delete()
        }
    }
}
