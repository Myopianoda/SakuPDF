package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.PdfDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

object MergePdfConverter {

    private const val TAG = "SakuPDF_MergeConverter"

    suspend fun merge(
        context: Context,
        contentResolver: ContentResolver,
        pdfItems: List<PdfDocument>,
        targetUri: Uri,
        outputFilename: String,
        onProgress: (ConversionProgress) -> Unit
    ): Result<ConversionResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Starting PDF merge for ${pdfItems.size} files.")

        if (pdfItems.size < 2) {
            AppLogger.e(TAG, "Merge error: Fewer than 2 files provided.")
            return@withContext Result.failure(Exception("Pilih setidaknya 2 file PDF untuk digabungkan."))
        }

        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Throwable) {}

        val totalFiles = pdfItems.size
        val tempFiles = mutableListOf<File>()
        var outputStream: OutputStream? = null

        val sanitizedFilename = PdfMathUtils.sanitizeFilename(outputFilename.ifBlank { "PDF_Gabungan_Document.pdf" })

        try {
            val merger = PDFMergerUtility()
            var accumulatedPages = 0

            pdfItems.forEachIndexed { index, pdfDoc ->
                coroutineContext.ensureActive()

                val currentStep = index + 1
                val progressPercent = PdfMathUtils.calculateProgressPercentage(index, totalFiles)
                onProgress(
                    ConversionProgress(
                        processedPages = index,
                        totalPages = totalFiles,
                        percentage = progressPercent,
                        currentFileName = pdfDoc.name,
                        title = "Membaca $currentStep dari $totalFiles file"
                    )
                )

                val uri = pdfDoc.uri ?: run {
                    AppLogger.e(TAG, "File PDF ${pdfDoc.name} has null URI.")
                    throw Exception("File PDF ${pdfDoc.name} tidak memiliki lokasi yang valid.")
                }

                // Password protection check
                if (pdfDoc.isPdf && PdfDocumentInspector.isPdfPasswordProtected(contentResolver, uri)) {
                    AppLogger.e(TAG, "File ${pdfDoc.name} is password protected.")
                    throw Exception("File PDF dilindungi kata sandi dan tidak dapat digabungkan.")
                }

                // Copy URI to private temporary cache file
                val tempFile = PdfTemporaryFileManager.createTempPdfFileFromUri(context, contentResolver, uri)
                    ?: run {
                        AppLogger.e(TAG, "Failed to create temp file for ${pdfDoc.name}")
                        throw Exception("File PDF ${pdfDoc.name} tidak dapat dibaca.")
                    }
                tempFiles.add(tempFile)

                // Inspect loaded document via PDFBox
                var pdDoc: PDDocument? = null
                try {
                    pdDoc = PDDocument.load(tempFile)
                    if (pdDoc.isEncrypted) {
                        throw Exception("File PDF dilindungi kata sandi dan tidak dapat digabungkan.")
                    }
                    val pagesCount = pdDoc.numberOfPages
                    if (pagesCount <= 0) {
                        throw Exception("File PDF ${pdfDoc.name} tidak valid atau kosong.")
                    }
                    accumulatedPages += pagesCount
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                        throw Exception("File PDF dilindungi kata sandi dan tidak dapat digabungkan.")
                    }
                    throw Exception("File PDF ${pdfDoc.name} tidak valid atau rusak.")
                } finally {
                    try { pdDoc?.close() } catch (_: Exception) {}
                }

                merger.addSource(tempFile)
                AppLogger.d(TAG, "Added source ${pdfDoc.name} ($currentStep / $totalFiles)")
            }

            coroutineContext.ensureActive()

            onProgress(
                ConversionProgress(
                    processedPages = totalFiles,
                    totalPages = totalFiles,
                    percentage = 0.9f,
                    currentFileName = sanitizedFilename,
                    title = "Menggabungkan halaman PDF"
                )
            )

            // Open target output stream
            outputStream = contentResolver.openOutputStream(targetUri, "w")
                ?: run {
                    AppLogger.e(TAG, "Unable to open target output stream for targetUri: $targetUri")
                    throw Exception("Tidak dapat membuat file PDF di lokasi yang dipilih.")
                }

            merger.destinationStream = outputStream
            AppLogger.d(TAG, "Executing merger.mergeDocuments()")
            merger.mergeDocuments(null)

            outputStream.flush()
            outputStream.close()
            outputStream = null
            AppLogger.d(TAG, "Output stream flushed and closed.")

            coroutineContext.ensureActive()

            onProgress(
                ConversionProgress(
                    processedPages = totalFiles,
                    totalPages = totalFiles,
                    percentage = 1.0f,
                    currentFileName = sanitizedFilename,
                    title = "Menyimpan PDF hasil"
                )
            )

            // Verify written output size
            val bytesSize = contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                pfd.statSize
            } ?: run {
                AppLogger.e(TAG, "Unable to read file descriptor for merged PDF.")
                throw Exception("Gagal memverifikasi file PDF yang digabungkan.")
            }

            if (bytesSize <= 0) {
                AppLogger.e(TAG, "Merged PDF size is 0 bytes.")
                throw Exception("File PDF hasil penggabungan kosong (0 bytes).")
            }

            val kb = bytesSize / 1024f
            val fileSizeFormatted = if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)

            AppLogger.d(TAG, "PDF Merge success: $sanitizedFilename, Pages: $accumulatedPages, Size: $fileSizeFormatted")

            Result.success(
                ConversionResult(
                    uri = targetUri,
                    filename = sanitizedFilename,
                    sizeFormatted = fileSizeFormatted,
                    pages = accumulatedPages
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Merge failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            // Clean up all temporary input files
            tempFiles.forEach { tempFile ->
                PdfTemporaryFileManager.deleteTempFile(tempFile)
            }
        }
    }
}
