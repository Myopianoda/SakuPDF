package com.sakupdf.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.CompressPdfConverter
import com.sakupdf.app.model.CompressionLevel
import com.sakupdf.app.model.ConversionProgress
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
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
class CompressPdfVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createPdfWithLargeLosslessImage(file: File): Bitmap {
        val doc = PDDocument()
        val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(800 * 800)
        val rnd = java.util.Random(12345)
        for (i in pixels.indices) {
            pixels[i] = rnd.nextInt() or (0xFF shl 24)
        }
        bmp.setPixels(pixels, 0, 800, 0, 0, 800, 800)

        try {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            // Embed lossless (uncompressed Flate bitmap)
            val pdImage = LosslessFactory.createFromImage(doc, bmp)
            val cs = PDPageContentStream(doc, page)
            cs.drawImage(pdImage, 50f, 200f, 495f, 495f)

            // Vector text marker
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
            cs.newLineAtOffset(50f, 750f)
            cs.showText("SakuPDF Selectable Compression Test Text")
            cs.endText()
            cs.close()

            doc.save(file)
        } finally {
            doc.close()
        }
        return bmp
    }

    private fun createPureTextPdf(file: File) {
        val doc = PDDocument()
        try {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val cs = PDPageContentStream(doc, page)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
            cs.newLineAtOffset(50f, 750f)
            cs.showText("Pure Vector Text Document")
            cs.endText()
            cs.close()
            doc.save(file)
        } finally {
            doc.close()
        }
    }

    @Test
    fun testCompressionReducesSizeWhenImagesPresent() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "compress_src_img_${UUID.randomUUID()}.pdf")
            val bmp = createPdfWithLargeLosslessImage(sourceFile)
            bmp.recycle()
            val inputBytes = sourceFile.length()
            val outputFile = File(context.cacheDir, "compress_out_${UUID.randomUUID()}.pdf")

            val progressReports = mutableListOf<ConversionProgress>()

            val result = CompressPdfConverter.compress(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "DokumenBesar.pdf",
                compressionLevel = CompressionLevel.BALANCED,
                destinationUri = Uri.fromFile(outputFile),
                onProgress = { progressReports.add(it) }
            )

            assertTrue("Compression must succeed", result.isSuccess)
            val compResult = result.getOrNull()
            assertNotNull(compResult)
            assertFalse("Should not report already optimized for large uncompressed images", compResult!!.isAlreadyOptimized)
            assertTrue("Percentage change must be positive", compResult.percentageChange > 0f)

            val outputBytes = outputFile.length()
            assertTrue("Output file must exist", outputFile.exists())
            assertTrue("Output must be significantly smaller than uncompressed input", outputBytes < inputBytes)

            // Verify reopening with PDFBox
            val reopenedDoc = PDDocument.load(outputFile)
            assertEquals("Page count must be preserved", 1, reopenedDoc.numberOfPages)

            // Verify selectable text remains intact
            val stripper = PDFTextStripper()
            val text = stripper.getText(reopenedDoc)
            assertTrue("Selectable text must be preserved", text.contains("SakuPDF Selectable Compression Test Text"))
            reopenedDoc.close()

            println("=== COMPRESSION REAL AUDIT ===")
            println("INPUT BYTES: $inputBytes")
            println("OUTPUT BYTES: $outputBytes")
            println("PERCENTAGE CHANGE: ${compResult.percentageChange}%")
            println("STATUS MESSAGE: ${compResult.statusMessage}")
            println("==============================")

            sourceFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun testNoFalseCompressionClaimWhenAlreadyOptimized() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "compress_src_puretext_${UUID.randomUUID()}.pdf")
            createPureTextPdf(sourceFile)
            val inputBytes = sourceFile.length()
            val outputFile = File(context.cacheDir, "compress_out_puretext_${UUID.randomUUID()}.pdf")

            val result = CompressPdfConverter.compress(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "TextOnly.pdf",
                compressionLevel = CompressionLevel.HIGH,
                destinationUri = Uri.fromFile(outputFile),
                onProgress = {}
            )

            assertTrue("Operation must succeed", result.isSuccess)
            val compResult = result.getOrNull()
            assertNotNull(compResult)

            // Verify no false claim: must retain original and report already optimal
            assertTrue("Must report already optimized when compression is not beneficial", compResult!!.isAlreadyOptimized)
            assertEquals(0f, compResult.percentageChange, 0.01f)
            assertEquals(inputBytes, compResult.outputBytes)
            assertTrue("Status must state already optimal", compResult.statusMessage.contains("optimal", ignoreCase = true))

            println("=== COMPRESSION NO FALSE CLAIM AUDIT ===")
            println("INPUT BYTES: $inputBytes")
            println("OUTPUT BYTES: ${outputFile.length()}")
            println("PERCENTAGE CHANGE: ${compResult.percentageChange}%")
            println("IS ALREADY OPTIMIZED: ${compResult.isAlreadyOptimized}")
            println("========================================")

            sourceFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun testCompressRejectsCorruptPdf() {
        runBlocking {
            val corrupt = File(context.cacheDir, "corrupt_${UUID.randomUUID()}.pdf")
            FileOutputStream(corrupt).use { it.write("INVALID_CONTENT".toByteArray()) }

            val result = CompressPdfConverter.compress(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(corrupt),
                sourceName = "corrupt.pdf",
                compressionLevel = CompressionLevel.BALANCED,
                destinationUri = Uri.fromFile(File(context.cacheDir, "out.pdf")),
                onProgress = {}
            )

            assertTrue("Must fail on corrupt file", result.isFailure)
            corrupt.delete()
        }
    }
}
