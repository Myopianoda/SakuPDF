package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.ImageItem
import com.sakupdf.app.model.PdfSettings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

object ImageToPdfConverter {

    private const val TAG = "SakuPDF_Converter"

    suspend fun convert(
        contentResolver: ContentResolver,
        images: List<ImageItem>,
        settings: PdfSettings,
        targetUri: Uri,
        context: Context? = null,
        onProgress: (ConversionProgress) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Conversion started for ${images.size} images to targetUri: $targetUri")

        if (images.isEmpty()) {
            AppLogger.e(TAG, "Finalization error: No images selected.")
            return@withContext Result.failure(Exception("Tidak ada gambar yang dipilih."))
        }

        if (context != null) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Throwable) {}
        }

        val totalPages = images.size
        val pdfDocument = PDDocument()
        var outputStream: OutputStream? = null

        val maxDimension = PdfMathUtils.calculateMaxDimension(settings.quality)
        val marginPoints = PdfMathUtils.calculateMarginPoints(settings.margin)
        val jpegQuality = when {
            settings.quality.contains("Hemat", ignoreCase = true) -> 0.65f
            settings.quality.contains("Tinggi", ignoreCase = true) -> 0.88f
            else -> 0.78f // "Seimbang (Default)"
        }

        val sanitizedFileName = PdfMathUtils.sanitizeFilename(
            settings.filename.ifBlank { "SakuPDF_Document.pdf" }
        )

        try {
            images.forEachIndexed { index, imageItem ->
                coroutineContext.ensureActive()

                val pageNumber = index + 1
                val progressPercent = PdfMathUtils.calculateProgressPercentage(index, totalPages)
                onProgress(
                    ConversionProgress(
                        processedPages = index,
                        totalPages = totalPages,
                        percentage = progressPercent,
                        currentFileName = imageItem.name,
                        title = "Membuat PDF"
                    )
                )

                // 1. Prepare PDImageXObject efficiently
                var pdImage: PDImageXObject? = null
                val isJpeg = isJpegImage(contentResolver, imageItem.uri, imageItem.name)
                val exifRotation = ImageDecoderUtils.getExifOrientationDegrees(contentResolver, imageItem.uri)

                // Direct pass-through check: Untouched original JPEG if unrotated and within maxDimension
                if (isJpeg && imageItem.rotation == 0f && exifRotation == 0f) {
                    val (rawW, rawH) = getImageDimensions(contentResolver, imageItem.uri)
                    if (rawW > 0 && rawH > 0 && max(rawW, rawH) <= maxDimension) {
                        try {
                            contentResolver.openInputStream(imageItem.uri)?.use { stream ->
                                pdImage = JPEGFactory.createFromStream(pdfDocument, stream)
                            }
                            AppLogger.d(TAG, "Direct JPEG pass-through embedding for: ${imageItem.name}")
                        } catch (e: Exception) {
                            AppLogger.d(TAG, "createFromStream failed, fallback to decode: ${e.message}")
                            pdImage = null
                        }
                    }
                }

                // If not eligible for direct pass-through, decode memory-safely and encode
                if (pdImage == null) {
                    val bitmap = try {
                        val decoded = ImageDecoderUtils.decodeDownsampledBitmap(
                            contentResolver = contentResolver,
                            uri = imageItem.uri,
                            maxDimension = maxDimension,
                            userRotationDegrees = imageItem.rotation
                        )
                        AppLogger.d(TAG, "Image decoded successfully: ${imageItem.name}")
                        decoded
                    } catch (e: OutOfMemoryError) {
                        AppLogger.e(TAG, "Finalization error: OutOfMemoryError for ${imageItem.name}")
                        throw Exception("Memori perangkat tidak cukup untuk memproses ${imageItem.name}.")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Finalization error: Exception reading ${imageItem.name}: ${e.message}")
                        throw Exception("Gambar ${imageItem.name} tidak dapat dibaca atau rusak.")
                    } ?: run {
                        AppLogger.e(TAG, "Finalization error: Bitmap is null for ${imageItem.name}")
                        throw Exception("Gambar ${imageItem.name} tidak dapat dibaca.")
                    }

                    try {
                        pdImage = if (bitmap.hasAlpha()) {
                            LosslessFactory.createFromImage(pdfDocument, bitmap)
                        } else {
                            JPEGFactory.createFromImage(pdfDocument, bitmap, jpegQuality)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                // 2. Create Page and layout image
                val imgW = pdImage.width
                val imgH = pdImage.height

                val rawSize = PdfMathUtils.calculatePageSizePoints(
                    sizeOption = settings.pageSize,
                    imageWidth = imgW,
                    imageHeight = imgH
                )
                val pageSize = PdfMathUtils.applyOrientation(
                    pageSize = rawSize,
                    orientationOption = settings.orientation
                )

                val pageW = pageSize.first.coerceAtLeast(100f)
                val pageH = pageSize.second.coerceAtLeast(100f)

                val page = PDPage(PDRectangle(pageW, pageH))
                pdfDocument.addPage(page)

                val containerWidth = (pageW - (marginPoints * 2f)).coerceAtLeast(10f)
                val containerHeight = (pageH - (marginPoints * 2f)).coerceAtLeast(10f)

                val destRect = PdfMathUtils.calculateFitCenterRect(
                    imageWidth = imgW,
                    imageHeight = imgH,
                    containerWidth = containerWidth,
                    containerHeight = containerHeight
                )
                destRect.offset(marginPoints, marginPoints)

                val cs = PDPageContentStream(pdfDocument, page)
                try {
                    // White page background
                    cs.setNonStrokingColor(1f, 1f, 1f)
                    cs.addRect(0f, 0f, pageW, pageH)
                    cs.fill()

                    // PDF coordinate system origin is bottom-left (Y grows upwards)
                    val drawX = destRect.left
                    val drawY = pageH - destRect.bottom
                    val drawW = destRect.width()
                    val drawH = destRect.height()

                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                } finally {
                    cs.close()
                }

                AppLogger.d(TAG, "Page finished: $pageNumber of $totalPages")
            }

            coroutineContext.ensureActive()

            onProgress(
                ConversionProgress(
                    processedPages = totalPages,
                    totalPages = totalPages,
                    percentage = 1.0f,
                    currentFileName = sanitizedFileName,
                    title = "Menyimpan File"
                )
            )

            // Step 1: Open Output Stream
            outputStream = contentResolver.openOutputStream(targetUri, "w")
                ?: run {
                    AppLogger.e(TAG, "Finalization error: Unable to open output stream for targetUri: $targetUri")
                    throw Exception("Tidak dapat membuat file PDF di lokasi yang dipilih.")
                }

            // Step 2: pdfDocument.save()
            AppLogger.d(TAG, "pdfDocument.save started")
            pdfDocument.save(outputStream)
            AppLogger.d(TAG, "pdfDocument.save completed")

            // Step 3: Flush and Close Output Stream
            outputStream.flush()
            outputStream.close()
            outputStream = null
            AppLogger.d(TAG, "Output stream closed")

            // Step 4: Close PDDocument
            pdfDocument.close()
            AppLogger.d(TAG, "pdfDocument closed")

            coroutineContext.ensureActive()

            // Step 5: Verify target URI can be read & get real output size
            val bytesSize = contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                pfd.statSize
            } ?: run {
                AppLogger.e(TAG, "Finalization error: Unable to read file descriptor for targetUri")
                throw Exception("Gagal memverifikasi file PDF yang dibuat.")
            }

            if (bytesSize <= 0) {
                AppLogger.e(TAG, "Finalization error: Created PDF size is 0 bytes")
                throw Exception("File PDF yang dibuat kosong (0 bytes).")
            }

            val kb = bytesSize / 1024f
            val fileSizeFormatted = if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)

            AppLogger.d(TAG, "Success state emitted: $sanitizedFileName, Size: $fileSizeFormatted")

            Result.success(
                ConversionResult(
                    uri = targetUri,
                    filename = sanitizedFileName,
                    sizeFormatted = fileSizeFormatted,
                    pages = totalPages
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Finalization error caught during conversion: ${e.message}")
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }

    private fun isJpegImage(contentResolver: ContentResolver, uri: Uri, fileName: String): Boolean {
        return try {
            val mime = contentResolver.getType(uri)
            if (mime != null) {
                mime.equals("image/jpeg", ignoreCase = true) || mime.equals("image/jpg", ignoreCase = true)
            } else {
                val lowerName = fileName.lowercase()
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
            }
        } catch (_: Exception) {
            val lowerName = fileName.lowercase()
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
        }
    }

    private fun getImageDimensions(contentResolver: ContentResolver, uri: Uri): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            Pair(options.outWidth, options.outHeight)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }
}
