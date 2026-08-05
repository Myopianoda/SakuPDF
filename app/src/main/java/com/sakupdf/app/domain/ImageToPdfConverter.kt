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

    suspend fun convert(
        contentResolver: ContentResolver,
        images: List<ImageItem>,
        settings: PdfSettings,
        targetUri: Uri,
        onProgress: (ConversionProgress) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        if (images.isEmpty()) {
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

                // Decode downsampled & rotated bitmap for single page
                val bitmap = try {
                    ImageDecoderUtils.decodeDownsampledBitmap(
                        contentResolver = contentResolver,
                        uri = imageItem.uri,
                        maxDimension = maxDimension,
                        userRotationDegrees = imageItem.rotation
                    )
                } catch (e: OutOfMemoryError) {
                    throw Exception("Memori perangkat tidak cukup untuk memproses ${imageItem.name}.")
                } catch (e: Exception) {
                    throw Exception("Gambar ${imageItem.name} tidak dapat dibaca atau rusak.")
                } ?: throw Exception("Gambar ${imageItem.name} tidak dapat dibaca.")

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
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // White page background
                    canvas.drawColor(Color.WHITE)

                    // Calculate printable area inside margins
                    val containerWidth = (pageW.toFloat() - (marginPoints * 2f)).coerceAtLeast(10f)
                    val containerHeight = (pageH.toFloat() - (marginPoints * 2f)).coerceAtLeast(10f)

                    val destRect = PdfMathUtils.calculateFitCenterRect(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        containerWidth = containerWidth,
                        containerHeight = containerHeight
                    )

                    // Offset rect by margins
                    destRect.offset(marginPoints, marginPoints)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, null, destRect, paint)

                    pdfDocument.finishPage(page)
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

            // Write PDF to output stream
            try {
                outputStream = contentResolver.openOutputStream(targetUri, "w")
                    ?: throw Exception("Tidak dapat membuat file PDF di lokasi yang dipilih.")
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            } catch (e: Exception) {
                throw Exception("Gagal menulis file PDF ke penyimpanan. ${e.localizedMessage ?: ""}")
            }

            // Determine file size
            val fileSizeFormatted = try {
                contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                    val bytes = pfd.statSize
                    val kb = bytes / 1024f
                    if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
                } ?: "PDF Baru"
            } catch (_: Exception) {
                "PDF Baru"
            }

            Result.success(
                ConversionResult(
                    uri = targetUri,
                    filename = sanitizedFileName,
                    sizeFormatted = fileSizeFormatted,
                    pages = totalPages
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            pdfDocument.close()
        }
    }
}
