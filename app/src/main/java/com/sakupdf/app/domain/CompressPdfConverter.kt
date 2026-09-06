package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.sakupdf.app.model.CompressionLevel
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.max

data class CompressResult(
    val conversionResult: ConversionResult,
    val inputBytes: Long,
    val outputBytes: Long,
    val percentageChange: Float,
    val isAlreadyOptimized: Boolean,
    val statusMessage: String
)

object CompressPdfConverter {

    private const val TAG = "SakuPDF_CompressConverter"

    suspend fun compress(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
        sourceName: String,
        compressionLevel: CompressionLevel,
        destinationUri: Uri? = null,
        onProgress: (ConversionProgress) -> Unit
    ): Result<CompressResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Starting PDF compression for $sourceName with level $compressionLevel")

        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Throwable) {}

        if (PdfDocumentInspector.isPdfPasswordProtected(contentResolver, sourceUri)) {
            AppLogger.e(TAG, "PDF is password protected.")
            return@withContext Result.failure(Exception("File PDF dilindungi kata sandi dan tidak dapat dikompres."))
        }

        val tempSourceFile = PdfTemporaryFileManager.createTempPdfFileFromUri(context, contentResolver, sourceUri)
            ?: run {
                AppLogger.e(TAG, "Failed to copy source PDF to cache.")
                return@withContext Result.failure(Exception("File PDF tidak dapat dibaca atau rusak."))
            }

        val inputBytes = tempSourceFile.length().coerceAtLeast(1L)
        val tempOptimizedFile = File(context.cacheDir, "temp_opt_${UUID.randomUUID()}.pdf")
        var document: PDDocument? = null

        try {
            document = try {
                PDDocument.load(tempSourceFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "PDDocument.load failed: ${e.message}")
                val msg = e.message ?: ""
                if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                    throw Exception("File PDF dilindungi kata sandi dan tidak dapat dikompres.")
                }
                throw Exception("File PDF rusak atau tidak dapat dibuka.")
            }

            if (document.isEncrypted) {
                throw Exception("File PDF dilindungi kata sandi dan tidak dapat dikompres.")
            }

            val totalPages = document.numberOfPages
            if (totalPages <= 0) {
                throw Exception("File PDF kosong atau tidak memiliki halaman.")
            }

            // Preset configuration
            val (qualityFloat, maxImageDimension) = when (compressionLevel) {
                CompressionLevel.HIGH -> Pair(0.80f, 1800)
                CompressionLevel.BALANCED -> Pair(0.65f, 1400)
                CompressionLevel.MINIMUM -> Pair(0.45f, 1000)
            }

            for (i in 0 until totalPages) {
                coroutineContext.ensureActive()

                val pageNum = i + 1
                val progressPercent = pageNum.toFloat() / totalPages.toFloat()
                onProgress(
                    ConversionProgress(
                        processedPages = i,
                        totalPages = totalPages,
                        percentage = progressPercent,
                        currentFileName = sourceName,
                        title = "Mengompres halaman $pageNum dari $totalPages"
                    )
                )

                val page = document.getPage(i)
                val resources = page.resources
                if (resources != null) {
                    val xObjectNames = resources.xObjectNames
                    for (cosName in xObjectNames) {
                        try {
                            val xobject = resources.getXObject(cosName)
                            if (xobject is PDImageXObject) {
                                val origW = xobject.width
                                val origH = xobject.height
                                val maxSide = max(origW, origH)

                                val originalBitmap = xobject.image
                                if (originalBitmap != null) {
                                    val bitmapToUse: Bitmap
                                    val needsScale = maxSide > maxImageDimension

                                    if (needsScale) {
                                        val scale = maxImageDimension.toFloat() / maxSide.toFloat()
                                        val targetW = (origW * scale).toInt().coerceAtLeast(1)
                                        val targetH = (origH * scale).toInt().coerceAtLeast(1)
                                        bitmapToUse = Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
                                    } else {
                                        bitmapToUse = originalBitmap
                                    }

                                    val compressedXImage = JPEGFactory.createFromImage(document, bitmapToUse, qualityFloat)
                                    resources.put(cosName, compressedXImage)

                                    if (needsScale && bitmapToUse != originalBitmap) {
                                        bitmapToUse.recycle()
                                    }
                                    originalBitmap.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.d(TAG, "Could not compress XObject $cosName: ${e.message}")
                        }
                    }
                }
            }

            coroutineContext.ensureActive()

            // Export to clean new document so orphaned/old image streams are omitted
            val cleanDoc = PDDocument()
            try {
                for (p in 0 until totalPages) {
                    cleanDoc.importPage(document.getPage(p))
                }
                cleanDoc.save(tempOptimizedFile)
            } finally {
                try { cleanDoc.close() } catch (_: Exception) {}
            }

            document.close()
            document = null

            val compressedBytes = tempOptimizedFile.length()
            AppLogger.d(TAG, "Raw compression result: inputBytes=$inputBytes, compressedBytes=$compressedBytes")

            // Compare input vs output size
            val isSmaller = compressedBytes < inputBytes
            val finalFileToUse: File
            val finalBytes: Long
            val percentageChange: Float
            val isAlreadyOptimized: Boolean
            val statusMessage: String

            if (isSmaller) {
                finalFileToUse = tempOptimizedFile
                finalBytes = compressedBytes
                percentageChange = ((inputBytes - compressedBytes).toFloat() / inputBytes.toFloat()) * 100f
                isAlreadyOptimized = false
                statusMessage = String.format(Locale.US, "Ukuran berhasil dikurangi %.1f%%", percentageChange)
            } else {
                // Do NOT falsely claim compression succeeded: Retain original!
                finalFileToUse = tempSourceFile
                finalBytes = inputBytes
                percentageChange = 0f
                isAlreadyOptimized = true
                statusMessage = "PDF sudah optimal. Ukuran asli dipertahankan."
            }

            AppLogger.d(
                TAG,
                "Compression audit: INPUT BYTES: $inputBytes, OUTPUT BYTES: $finalBytes, PERCENTAGE CHANGE: $percentageChange%, OPTIMIZED: $isAlreadyOptimized"
            )

            // Normalize filename
            val baseName = sourceName.substringBeforeLast(".pdf", sourceName).ifBlank { "Dokumen" }
            val sanitizedName = PdfMathUtils.sanitizeFilename("${baseName}_Kompres.pdf")

            val targetUri: Uri
            if (destinationUri != null) {
                targetUri = destinationUri
                contentResolver.openOutputStream(targetUri, "w")?.use { outStream ->
                    FileInputStream(finalFileToUse).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                    outStream.flush()
                } ?: throw Exception("Tidak dapat membuka lokasi penyimpanan untuk menyimpan PDF.")
            } else {
                val persistentFile = File(context.cacheDir, sanitizedName)
                FileInputStream(finalFileToUse).use { inStream ->
                    FileOutputStream(persistentFile).use { outStream ->
                        inStream.copyTo(outStream)
                        outStream.flush()
                    }
                }
                targetUri = Uri.fromFile(persistentFile)
            }

            val formattedSize = if (finalBytes > 0) {
                val kb = finalBytes / 1024f
                if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
            } else {
                "PDF"
            }

            onProgress(
                ConversionProgress(
                    processedPages = totalPages,
                    totalPages = totalPages,
                    percentage = 1.0f,
                    currentFileName = sanitizedName,
                    title = "Selesai mengompres PDF"
                )
            )

            val convResult = ConversionResult(
                uri = targetUri,
                filename = sanitizedName,
                sizeFormatted = formattedSize,
                pages = totalPages
            )

            Result.success(
                CompressResult(
                    conversionResult = convResult,
                    inputBytes = inputBytes,
                    outputBytes = finalBytes,
                    percentageChange = percentageChange,
                    isAlreadyOptimized = isAlreadyOptimized,
                    statusMessage = statusMessage
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "PDF compression failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { document?.close() } catch (_: Exception) {}
            PdfTemporaryFileManager.deleteTempFile(tempSourceFile)
            PdfTemporaryFileManager.deleteTempFile(tempOptimizedFile)
        }
    }
}
