package com.sakupdf.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.ImageToPdfConverter
import com.sakupdf.app.domain.MergePdfConverter
import com.sakupdf.app.domain.PdfDocumentInspector
import com.sakupdf.app.domain.PdfTemporaryFileManager
import com.sakupdf.app.model.ImageItem
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.model.PdfSettings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageToPdfAndMergeVerificationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createPhotoLikeBitmap(width: Int, height: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(Color.rgb(100, 150, 220), Color.rgb(220, 180, 100), Color.rgb(80, 180, 120), Color.rgb(200, 80, 80)),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = (height / 25f).coerceAtLeast(16f)
            isAntiAlias = true
        }
        canvas.drawText(label, 50f, height / 2f, textPaint)
        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 90): File {
        val file = File(context.cacheDir, "fixture_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun testAuditImageToPdfFileSizesAndQualitySettings() {
        runBlocking {
            // Create high-resolution camera images (e.g. 4000x3000, ~12MP)
            val imgFile1 = saveBitmapToCache(createPhotoLikeBitmap(4000, 3000, "Camera Photo 1 Landscape"))
            val imgFile2 = saveBitmapToCache(createPhotoLikeBitmap(3000, 4000, "Camera Photo 2 Portrait"))
            val imgFile3 = saveBitmapToCache(createPhotoLikeBitmap(800, 600, "Small Photo 3"))

        val totalSourceBytes = imgFile1.length() + imgFile2.length() + imgFile3.length()
        val images = listOf(
            ImageItem(id = "1", uri = Uri.fromFile(imgFile1), name = "Photo1.jpg"),
            ImageItem(id = "2", uri = Uri.fromFile(imgFile2), name = "Photo2.jpg"),
            ImageItem(id = "3", uri = Uri.fromFile(imgFile3), name = "Photo3.jpg")
        )

        val outputFile = File(context.cacheDir, "audit_output_${UUID.randomUUID()}.pdf")
        val outputUri = Uri.fromFile(outputFile)

        val settings = PdfSettings(
            filename = "AuditTest",
            pageSize = "A4",
            orientation = "Potret",
            margin = "Tanpa margin",
            quality = "Seimbang (Default)"
        )

        var progressReportCount = 0
        val result = ImageToPdfConverter.convert(
            contentResolver = context.contentResolver,
            images = images,
            settings = settings,
            targetUri = outputUri,
            onProgress = { progress ->
                progressReportCount++
            }
        )

        assertTrue("ImageToPdf conversion must succeed", result.isSuccess)
        val convResult = result.getOrNull()
        assertNotNull(convResult)
        assertEquals(3, convResult!!.pages)
        assertTrue(outputFile.exists())
        val outputBytes = outputFile.length()
        assertTrue("Output size must be > 0", outputBytes > 0)

        // Verify PDF can be reopened with PDFBox
        val pdDoc = PDDocument.load(outputFile)
        assertEquals(3, pdDoc.numberOfPages)
        pdDoc.close()

        println("=== AUDIT IMAGE TO PDF RESULTS ===")
        println("SOURCE FILES: 3")
        println("TOTAL SOURCE BYTES: $totalSourceBytes (${totalSourceBytes / 1024} KB)")
        println("OUTPUT BYTES: $outputBytes (${outputBytes / 1024} KB)")
        println("OUTPUT FORMATTED: ${convResult.sizeFormatted}")
        println("PROGRESS REPORTS: $progressReportCount")
        println("==================================")

        // Clean up
        imgFile1.delete()
        imgFile2.delete()
        imgFile3.delete()
        outputFile.delete()
        }
    }

    @Test
    fun testMergePdfWorkflowWithDeterministicPdfs() {
        runBlocking {
            // Create 2 test PDFs using PDFBox
            val pdf1File = File(context.cacheDir, "merge_src1_${UUID.randomUUID()}.pdf")
            val doc1 = PDDocument()
            val page1 = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc1.addPage(page1)
            val page2 = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc1.addPage(page2)
            doc1.save(pdf1File)
            doc1.close()

            val pdf2File = File(context.cacheDir, "merge_src2_${UUID.randomUUID()}.pdf")
            val doc2 = PDDocument()
            val page3 = com.tom_roush.pdfbox.pdmodel.PDPage()
            doc2.addPage(page3)
            doc2.save(pdf2File)
            doc2.close()

            val pdfItems = listOf(
                PdfDocument(id = "1", name = "Doc1.pdf", sizeFormatted = "10 KB", dateFormatted = "Today", pages = 2, uri = Uri.fromFile(pdf1File)),
                PdfDocument(id = "2", name = "Doc2.pdf", sizeFormatted = "5 KB", dateFormatted = "Today", pages = 1, uri = Uri.fromFile(pdf2File))
            )

            val mergedOutputFile = File(context.cacheDir, "merged_output_${UUID.randomUUID()}.pdf")
            val mergedOutputUri = Uri.fromFile(mergedOutputFile)

            val mergeResult = MergePdfConverter.merge(
                context = context,
                contentResolver = context.contentResolver,
                pdfItems = pdfItems,
                targetUri = mergedOutputUri,
                outputFilename = "FinalMerged.pdf",
                onProgress = {}
            )

            assertTrue("Merge must succeed", mergeResult.isSuccess)
            val res = mergeResult.getOrNull()
            assertNotNull(res)
            assertEquals(3, res!!.pages)
            assertEquals("FinalMerged.pdf", res.filename)

            // Verify with PDFBox
            val mergedPdDoc = PDDocument.load(mergedOutputFile)
            assertEquals(3, mergedPdDoc.numberOfPages)
            mergedPdDoc.close()

            // Clean up
            pdf1File.delete()
            pdf2File.delete()
            mergedOutputFile.delete()
        }
    }

    @Test
    fun testMergePdfRejectsFewerThanTwoFiles() {
        runBlocking {
            val singleDoc = listOf(
                PdfDocument(id = "1", name = "Single.pdf", sizeFormatted = "10 KB", dateFormatted = "Today", pages = 1, uri = Uri.parse("file:///dummy.pdf"))
            )
            val outputUri = Uri.parse("file:///dummy_out.pdf")
            val result = MergePdfConverter.merge(
                context = context,
                contentResolver = context.contentResolver,
                pdfItems = singleDoc,
                targetUri = outputUri,
                outputFilename = "Out.pdf",
                onProgress = {}
            )
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun testIntentConstructionForViewAndSend() {
        val testUri = Uri.parse("content://com.sakupdf.app.provider/test.pdf")

        // 1. ACTION_VIEW intent
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(testUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertEquals("application/pdf", viewIntent.type)
        assertEquals(testUri, viewIntent.data)
        assertTrue((viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        // 2. ACTION_SEND intent
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, testUri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("application/pdf", sendIntent.type)
        assertEquals(testUri, sendIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertTrue((sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
