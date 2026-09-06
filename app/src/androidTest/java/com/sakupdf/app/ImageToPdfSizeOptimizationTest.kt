package com.sakupdf.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sakupdf.app.domain.ImageToPdfConverter
import com.sakupdf.app.model.ImageItem
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
class ImageToPdfSizeOptimizationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createPhotoBitmap(width: Int, height: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Realistic photographic gradient + complex details so JPEG vs Flate behaves realistically
        val shader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.rgb(45, 95, 160),
                Color.rgb(180, 140, 90),
                Color.rgb(70, 140, 100),
                Color.rgb(190, 80, 60),
                Color.rgb(120, 60, 150)
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { this.shader = shader }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw multiple shapes, circles, textures to resemble photo detail
        val detailPaint = Paint()
        val rnd = java.util.Random(101)
        for (i in 0 until 120) {
            detailPaint.color = Color.argb(160, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            val cx = rnd.nextFloat() * width
            val cy = rnd.nextFloat() * height
            val radius = 10f + rnd.nextFloat() * (width / 15f)
            canvas.drawCircle(cx, cy, radius, detailPaint)
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = (height / 20f).coerceAtLeast(24f)
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText(label, 60f, height / 2f, textPaint)
        return bitmap
    }

    private fun saveJpeg(bitmap: Bitmap, quality: Int, exifOrientation: Int? = null): File {
        val file = File(context.cacheDir, "fixture_wa_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
        }
        bitmap.recycle()

        if (exifOrientation != null) {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
            exif.saveAttributes()
        }
        return file
    }

    @Test
    fun testRealWorldFourImagesSizeAudit() {
        runBlocking {
            // 4 WhatsApp / phone photo fixtures:
            // 1. WhatsApp standard landscape 1600x1200 (~400-600 KB)
            val img1 = saveJpeg(createPhotoBitmap(1600, 1200, "WhatsApp Photo 1 Landscape"), 85)
            // 2. WhatsApp standard portrait 1200x1600 (~400-600 KB)
            val img2 = saveJpeg(createPhotoBitmap(1200, 1600, "WhatsApp Photo 2 Portrait"), 85)
            // 3. Full HD 1920x1080 landscape (~600-800 KB)
            val img3 = saveJpeg(createPhotoBitmap(1920, 1080, "Phone Photo 3 1080p"), 88)
            // 4. Portrait 1200x1600 with EXIF Orientation 6 (Rotate 90 CW)
            val img4 = saveJpeg(createPhotoBitmap(1200, 1600, "EXIF Rotated Photo 4"), 85, ExifInterface.ORIENTATION_ROTATE_90)

            val sourceSizes = listOf(img1.length(), img2.length(), img3.length(), img4.length())
            val totalSourceBytes = sourceSizes.sum()

            val images = listOf(
                ImageItem(id = "1", uri = Uri.fromFile(img1), name = "WA_IMG_001.jpg"),
                ImageItem(id = "2", uri = Uri.fromFile(img2), name = "WA_IMG_002.jpg"),
                ImageItem(id = "3", uri = Uri.fromFile(img3), name = "WA_IMG_003.jpg"),
                ImageItem(id = "4", uri = Uri.fromFile(img4), name = "WA_IMG_004.jpg")
            )

            val outputFile = File(context.cacheDir, "test_output_wa_4img_${UUID.randomUUID()}.pdf")
            val outputUri = Uri.fromFile(outputFile)

            val settings = PdfSettings(
                filename = "WA_Batch",
                pageSize = "A4",
                orientation = "Potret",
                margin = "Tanpa margin",
                quality = "Seimbang (Default)"
            )

            val result = ImageToPdfConverter.convert(
                contentResolver = context.contentResolver,
                images = images,
                settings = settings,
                targetUri = outputUri,
                onProgress = {}
            )

            assertTrue("Conversion should succeed", result.isSuccess)
            val outputBytes = outputFile.length()
            val ratio = outputBytes.toDouble() / totalSourceBytes.toDouble()

            println("=== IMAGE TO PDF REAL-WORLD 4-IMG AUDIT ===")
            println("TOTAL SOURCE BYTES: $totalSourceBytes (${totalSourceBytes / 1024} KB)")
            println("OUTPUT PDF BYTES: $outputBytes (${outputBytes / 1024} KB)")
            println("OUTPUT/SOURCE RATIO: ${String.format(java.util.Locale.US, "%.2f", ratio)}")
            println("PAGE COUNT: ${images.size}")
            println("IMAGE DIMENSIONS: 1600x1200, 1200x1600, 1920x1080, 1200x1600(EXIF 90)")
            println("QUALITY SETTING: ${settings.quality}")
            println("===========================================")

            // Assert ratio is reasonable (not 12x bloated, < 1.5x)
            assertTrue("PDF size must not suffer pathological inflation (ratio was $ratio)", ratio < 1.5)

            // Reopen with PDFBox to verify validity & structure
            val doc = PDDocument.load(outputFile)
            assertEquals("Page count must be 4", 4, doc.numberOfPages)
            doc.close()

            // Reopen with Android PdfRenderer to verify rendering & visual quality
            val pfd = android.os.ParcelFileDescriptor.open(outputFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            assertEquals(4, renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val renderBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(renderBitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // Verify rendered page has non-zero pixels
                val samplePixel = renderBitmap.getPixel(page.width / 2, page.height / 2)
                assertNotEquals("Rendered page $i should not be transparent", 0, Color.alpha(samplePixel))
                renderBitmap.recycle()
                page.close()
            }
            renderer.close()
            pfd.close()

            img1.delete()
            img2.delete()
            img3.delete()
            img4.delete()
            outputFile.delete()
        }
    }

    @Test
    fun testPngWithAlphaPreservation() {
        runBlocking {
            // Create PNG with alpha channel (transparent background, colored circle)
            val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.TRANSPARENT)
            val paint = Paint().apply {
                color = Color.argb(200, 255, 50, 50)
                isAntiAlias = true
            }
            canvas.drawCircle(400f, 400f, 300f, paint)

            val pngFile = File(context.cacheDir, "fixture_alpha_${UUID.randomUUID()}.png")
            FileOutputStream(pngFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()

            val outputFile = File(context.cacheDir, "test_output_png_${UUID.randomUUID()}.pdf")
            val images = listOf(
                ImageItem(id = "png1", uri = Uri.fromFile(pngFile), name = "alpha_icon.png")
            )

            val result = ImageToPdfConverter.convert(
                contentResolver = context.contentResolver,
                images = images,
                settings = PdfSettings(filename = "AlphaTest", pageSize = "A4"),
                targetUri = Uri.fromFile(outputFile),
                onProgress = {}
            )

            assertTrue("PNG conversion should succeed", result.isSuccess)
            val doc = PDDocument.load(outputFile)
            assertEquals(1, doc.numberOfPages)
            doc.close()

            // Verify with PdfRenderer
            val pfd = android.os.ParcelFileDescriptor.open(outputFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val renderBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(renderBitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // Center should have color from circle
            val centerPixel = renderBitmap.getPixel(page.width / 2, page.height / 2)
            assertTrue("Red component should be high", Color.red(centerPixel) >= 200)
            renderBitmap.recycle()
            page.close()
            renderer.close()
            pfd.close()

            pngFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun testImageWithUserRotation() {
        runBlocking {
            val bmp = createPhotoBitmap(1200, 800, "User Rotated")
            val imgFile = saveJpeg(bmp, 85)

            val outputFile = File(context.cacheDir, "test_output_rotated_${UUID.randomUUID()}.pdf")
            val images = listOf(
                ImageItem(id = "rot1", uri = Uri.fromFile(imgFile), name = "rotated.jpg", rotation = 90f)
            )

            val result = ImageToPdfConverter.convert(
                contentResolver = context.contentResolver,
                images = images,
                settings = PdfSettings(filename = "RotatedTest", pageSize = "Sesuaikan Gambar"),
                targetUri = Uri.fromFile(outputFile),
                onProgress = {}
            )

            assertTrue("Rotated image conversion should succeed", result.isSuccess)
            val doc = PDDocument.load(outputFile)
            assertEquals(1, doc.numberOfPages)
            val page = doc.getPage(0)
            // Since original was 1200x800 and rotated 90 deg, rotated image is 800x1200 (portrait)
            assertTrue("Page should be portrait after 90 deg rotation", page.mediaBox.height > page.mediaBox.width)
            doc.close()

            imgFile.delete()
            outputFile.delete()
        }
    }
}

