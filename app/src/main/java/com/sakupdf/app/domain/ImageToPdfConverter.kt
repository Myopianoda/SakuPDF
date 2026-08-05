package com.sakupdf.app.domain

import android.content.ContentResolver
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.ImageItem
import com.sakupdf.app.model.PdfSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

object ImageToPdfConverter {

    private const val TAG = "SakuPDF_Converter"

    suspend fun convert(
        contentResolver: ContentResolver,
        images: List<ImageItem>,
        settings: PdfSettings,
        targetUri: Uri,
        onProgress: (ConversionProgress) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Conversion started for ${images.size} images to targetUri: $targetUri")

        if (images.isEmpty()) {
            AppLogger.e(TAG, "Finalization error: No images selected.")
            return@withContext Result.failure(Exception("Tidak ada gambar yang dipilih."))
        }

        val totalPages = images.size
        val pdfDocument = PdfDocument()
        var outputStream: OutputStream? = null

        val maxDimension = PdfMathUtils.calculateMaxDimension(settings.quality)
        val marginPoints = PdfMathUtils.calculateMarginPoints(settings.margin)

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

                // Decode downsampled & rotated bitmap
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

                var page: PdfDocument.Page? = null
                try {
                    val rawSize = PdfMathUtils.calculatePageSizePoints(
                        sizeOption = settings.pageSize,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height
                    )
                    val pageSize = PdfMathUtils.applyOrientation(
                        pageSize = rawSize,
                        orientationOption = settings.orientation
                    )

                    val pageW = pageSize.first.toInt().coerceAtLeast(100)
                    val pageH = pageSize.second.toInt().coerceAtLeast(100)

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()

                    AppLogger.d(TAG, "Page started: $pageNumber of $totalPages")
                    page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // White page background
                    canvas.drawColor(Color.WHITE)

                    // Printable container calculation
                    val containerWidth = (pageW.toFloat() - (marginPoints * 2f)).coerceAtLeast(10f)
                    val containerHeight = (pageH.toFloat() - (marginPoints * 2f)).coerceAtLeast(10f)

                    val destRect = PdfMathUtils.calculateFitCenterRect(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        containerWidth = containerWidth,
                        containerHeight = containerHeight
                    )

                    destRect.offset(marginPoints, marginPoints)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, null, destRect, paint)

                    pdfDocument.finishPage(page)
                    page = null
                    AppLogger.d(TAG, "Page finished: $pageNumber of $totalPages")
                } catch (e: Exception) {
                    if (page != null) {
                        try { pdfDocument.finishPage(page) } catch (_: Exception) {}
                    }
                    AppLogger.e(TAG, "Finalization error rendering page $pageNumber: ${e.message}")
                    throw e
                } finally {
                    bitmap.recycle()
                }
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

            // Step 2: PdfDocument.writeTo()
            AppLogger.d(TAG, "PdfDocument.writeTo started")
            pdfDocument.writeTo(outputStream)
            AppLogger.d(TAG, "PdfDocument.writeTo completed")

            // Step 3: Flush and Close Output Stream
            outputStream.flush()
            outputStream.close()
            outputStream = null
            AppLogger.d(TAG, "Output stream closed")

            // Step 4: Close PdfDocument
            pdfDocument.close()
            AppLogger.d(TAG, "PdfDocument closed")

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
}
