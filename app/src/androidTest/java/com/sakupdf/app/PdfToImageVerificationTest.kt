package com.sakupdf.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.PdfToImageConverter
import com.sakupdf.app.model.ConversionProgress
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfToImageVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createMixedOrientationPdf(file: File) {
        val doc = PDDocument()
        try {
            // Page 1: Portrait A4 (595 x 842)
            val p1 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            var cs = PDPageContentStream(doc, p1)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
            cs.newLineAtOffset(50f, 750f)
            cs.showText("Page 1 Portrait A4")
            cs.endText()
            cs.close()

            // Page 2: Landscape A4 (842 x 595)
            val p2 = PDPage(PDRectangle(842f, 595f))
            doc.addPage(p2)
            cs = PDPageContentStream(doc, p2)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
            cs.newLineAtOffset(50f, 500f)
            cs.showText("Page 2 Landscape A4")
            cs.endText()
            cs.close()

            // Page 3: Square (600 x 600)
            val p3 = PDPage(PDRectangle(600f, 600f))
            doc.addPage(p3)
            cs = PDPageContentStream(doc, p3)
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
            cs.newLineAtOffset(50f, 500f)
            cs.showText("Page 3 Square Custom")
            cs.endText()
            cs.close()

            doc.save(file)
        } finally {
            doc.close()
        }
    }

    @Test
    fun testConvertAllPagesToJpgWithStandardQuality() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "pdf_to_img_src_${UUID.randomUUID()}.pdf")
            createMixedOrientationPdf(sourceFile)
            val outputDir = File(context.cacheDir, "pdf_to_img_out_jpg_${UUID.randomUUID()}")
            outputDir.mkdirs()

            val progressReports = mutableListOf<ConversionProgress>()

            val result = PdfToImageConverter.convert(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "LaporanCampur.pdf",
                format = "JPG",
                quality = "Standar",
                selectedPages = emptyList(), // all pages
                destinationTreeUri = Uri.fromFile(outputDir),
                onProgress = { progressReports.add(it) }
            )

            assertTrue("Conversion must succeed", result.isSuccess)
            val imgResult = result.getOrNull()
            assertNotNull(imgResult)
            assertEquals(3, imgResult!!.outputFilesCount)
            assertEquals(3, imgResult.outputUris.size)
            assertTrue(progressReports.isNotEmpty())

            // Verify files
            for (p in 1..3) {
                val fileName = String.format("LaporanCampur_page_%03d.jpg", p)
                val imgFile = File(outputDir, fileName)
                assertTrue("File $fileName must exist", imgFile.exists())
                assertTrue("File size must be > 0", imgFile.length() > 0)

                // Decode bitmap
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                assertNotNull("Bitmap must decode successfully", bitmap)
                assertTrue("Width must be > 0", bitmap.width > 0)
                assertTrue("Height must be > 0", bitmap.height > 0)

                // Verify orientation preservation
                when (p) {
                    1 -> assertTrue("Page 1 must be portrait", bitmap.height > bitmap.width)
                    2 -> assertTrue("Page 2 must be landscape", bitmap.width > bitmap.height)
                    3 -> assertTrue("Page 3 must be approx square", kotlin.math.abs(bitmap.width - bitmap.height) < 10)
                }

                // Verify JPG has white background (not black/transparent)
                val pixel = bitmap.getPixel(10, 10)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                assertTrue("Background must be bright/white", red > 200 && green > 200 && blue > 200)

                bitmap.recycle()
            }

            println("=== PDF TO IMAGE JPG AUDIT ===")
            println("SOURCE BYTES: ${sourceFile.length()}")
            println("TOTAL OUTPUT BYTES: ${imgResult.totalOutputBytes}")
            println("FILES COUNT: ${imgResult.outputFilesCount}")
            println("==============================")

            sourceFile.delete()
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun testConvertSelectedPagesToPngWithHighQuality() {
        runBlocking {
            val sourceFile = File(context.cacheDir, "pdf_to_img_src_png_${UUID.randomUUID()}.pdf")
            createMixedOrientationPdf(sourceFile)
            val outputDir = File(context.cacheDir, "pdf_to_img_out_png_${UUID.randomUUID()}")
            outputDir.mkdirs()

            val result = PdfToImageConverter.convert(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(sourceFile),
                sourceName = "SelectedPages.pdf",
                format = "PNG",
                quality = "Tinggi",
                selectedPages = listOf(1, 3),
                destinationTreeUri = Uri.fromFile(outputDir),
                onProgress = {}
            )

            assertTrue(result.isSuccess)
            val imgResult = result.getOrNull()
            assertNotNull(imgResult)
            assertEquals(2, imgResult!!.outputFilesCount)

            // Verify Page 1 PNG
            val p1File = File(outputDir, "SelectedPages_page_001.png")
            assertTrue(p1File.exists())
            val b1 = BitmapFactory.decodeFile(p1File.absolutePath)
            assertNotNull(b1)
            // A4 portrait scaled by 2.0x
            assertEquals(1190, b1.width)
            assertTrue("Height must be scaled to ~1682-1684", b1.height in 1680..1685)
            b1.recycle()

            // Verify Page 3 PNG
            val p3File = File(outputDir, "SelectedPages_page_003.png")
            assertTrue(p3File.exists())
            val b3 = BitmapFactory.decodeFile(p3File.absolutePath)
            assertNotNull(b3)
            assertEquals(1200, b3.width)
            assertEquals(1200, b3.height)
            b3.recycle()

            sourceFile.delete()
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun testPdfToImageRejectsCorruptPdf() {
        runBlocking {
            val corrupt = File(context.cacheDir, "corrupt_${UUID.randomUUID()}.pdf")
            FileOutputStream(corrupt).use { it.write("INVALID_CONTENT".toByteArray()) }

            val result = PdfToImageConverter.convert(
                context = context,
                contentResolver = context.contentResolver,
                sourceUri = Uri.fromFile(corrupt),
                sourceName = "Corrupt.pdf",
                format = "JPG",
                quality = "Standar",
                selectedPages = emptyList(),
                destinationTreeUri = Uri.fromFile(context.cacheDir),
                onProgress = {}
            )

            assertTrue(result.isFailure)
            corrupt.delete()
        }
    }
}
