package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

data class PdfToImageResult(
    val conversionResult: ConversionResult,
    val outputFilesCount: Int,
    val totalOutputBytes: Long,
    val outputUris: List<Uri>,
    val imageDimensions: List<Pair<Int, Int>>
)

object PdfToImageConverter {

    private const val TAG = "SakuPDF_PdfToImage"

    suspend fun convert(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
        sourceName: String,
        format: String, // "JPG" or "PNG"
        quality: String, // "Standar" or "Tinggi"
        selectedPages: List<Int>, // 1-indexed
        destinationTreeUri: Uri? = null,
        singleTargetUri: Uri? = null,
        onProgress: (ConversionProgress) -> Unit
    ): Result<PdfToImageResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Starting PDF to Image: format=$format, quality=$quality for $sourceName")

        if (PdfDocumentInspector.isPdfPasswordProtected(contentResolver, sourceUri)) {
            AppLogger.e(TAG, "PDF is password protected.")
            return@withContext Result.failure(Exception("File PDF dilindungi kata sandi dan tidak dapat diubah ke gambar."))
        }

        val tempSourceFile = PdfTemporaryFileManager.createTempPdfFileFromUri(context, contentResolver, sourceUri)
            ?: run {
                AppLogger.e(TAG, "Failed to copy source PDF to cache.")
                return@withContext Result.failure(Exception("File PDF tidak dapat dibaca atau rusak."))
            }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val createdOutputUris = mutableListOf<Uri>()
        val outputDimensions = mutableListOf<Pair<Int, Int>>()
        var totalOutputBytes = 0L

        try {
            pfd = ParcelFileDescriptor.open(tempSourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = try {
                PdfRenderer(pfd)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to create PdfRenderer: ${e.message}")
                throw Exception("File PDF rusak atau tidak dapat dibuka.")
            }

            val totalPages = renderer.pageCount
            if (totalPages <= 0) {
                throw Exception("File PDF kosong atau tidak memiliki halaman.")
            }

            val targetPages = if (selectedPages.isEmpty()) {
                (1..totalPages).toList()
            } else {
                selectedPages.filter { it in 1..totalPages }.distinct()
            }

            if (targetPages.isEmpty()) {
                throw Exception("Pilih setidaknya 1 halaman untuk diubah ke gambar.")
            }

            val isJpg = format.equals("JPG", ignoreCase = true) || format.equals("JPEG", ignoreCase = true)
            val ext = if (isJpg) "jpg" else "png"
            val mimeType = if (isJpg) "image/jpeg" else "image/png"
            val compressFormat = if (isJpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

            val isHighQuality = quality.contains("Tinggi", ignoreCase = true)
            val scaleFactor = if (isHighQuality) 2.0f else 1.25f
            val compressQuality = if (isJpg) (if (isHighQuality) 95 else 85) else 100

            val baseName = sourceName.substringBeforeLast(".pdf", sourceName).ifBlank { "Dokumen" }
            val sanitizedBase = PdfMathUtils.sanitizeFilename(baseName).removeSuffix(".pdf")

            val totalJobs = targetPages.size
            AppLogger.d(TAG, "Rendering $totalJobs pages to $format (scale=$scaleFactor)")

            targetPages.forEachIndexed { index, pageNum ->
                coroutineContext.ensureActive()

                val currentStep = index + 1
                val outputFilename = String.format("%s_page_%03d.%s", sanitizedBase, pageNum, ext)
                val progressPercent = currentStep.toFloat() / totalJobs.toFloat()

                onProgress(
                    ConversionProgress(
                        processedPages = index,
                        totalPages = totalJobs,
                        percentage = progressPercent,
                        currentFileName = outputFilename,
                        title = "Merender halaman $currentStep dari $totalJobs"
                    )
                )

                val pageIndex = pageNum - 1
                var page: PdfRenderer.Page? = null
                var bitmap: Bitmap? = null
                var outputStream: OutputStream? = null

                try {
                    page = renderer.openPage(pageIndex)
                    val renderW = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                    val renderH = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                    bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    if (isJpg) {
                        // White background for JPEG to avoid black transparent rendering
                        canvas.drawColor(Color.WHITE)
                    }

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    outputDimensions.add(Pair(renderW, renderH))

                    val outputUri: Uri
                    if (singleTargetUri != null && totalJobs == 1) {
                        outputUri = singleTargetUri
                        outputStream = contentResolver.openOutputStream(outputUri, "w")
                            ?: throw Exception("Gagal membuka lokasi penyimpanan gambar.")
                    } else if (destinationTreeUri != null) {
                        if (destinationTreeUri.scheme == "file") {
                            val outDir = File(destinationTreeUri.path ?: context.cacheDir.path)
                            outDir.mkdirs()
                            val outFile = File(outDir, outputFilename)
                            outputStream = FileOutputStream(outFile)
                            outputUri = Uri.fromFile(outFile)
                        } else {
                            val docUri = createDocumentInTree(contentResolver, destinationTreeUri, mimeType, outputFilename)
                                ?: throw Exception("Gagal membuat berkas $outputFilename di folder tujuan.")
                            outputUri = docUri
                            outputStream = contentResolver.openOutputStream(outputUri, "w")
                                ?: throw Exception("Gagal membuka stream untuk berkas $outputFilename.")
                        }
                    } else {
                        val outFile = File(context.cacheDir, outputFilename)
                        outputStream = FileOutputStream(outFile)
                        outputUri = Uri.fromFile(outFile)
                    }

                    bitmap.compress(compressFormat, compressQuality, outputStream)
                    outputStream.flush()
                    createdOutputUris.add(outputUri)

                    var writtenBytes = 0L
                    if (outputUri.scheme == "file") {
                        writtenBytes = File(outputUri.path ?: "").length()
                    } else {
                        try {
                            contentResolver.openFileDescriptor(outputUri, "r")?.use { fd ->
                                writtenBytes = fd.statSize
                            }
                        } catch (_: Exception) {}
                    }
                    totalOutputBytes += writtenBytes
                } finally {
                    try { outputStream?.close() } catch (_: Exception) {}
                    bitmap?.recycle()
                    try { page?.close() } catch (_: Exception) {}
                }
            }

            coroutineContext.ensureActive()

            val primaryUri = createdOutputUris.firstOrNull() ?: sourceUri
            val primaryName = if (totalJobs == 1) String.format("%s_page_%03d.%s", sanitizedBase, targetPages.first(), ext) else "$totalJobs Gambar ($format)"
            val formattedSize = if (totalOutputBytes > 0) {
                val kb = totalOutputBytes / 1024f
                if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
            } else {
                format
            }

            onProgress(
                ConversionProgress(
                    processedPages = totalJobs,
                    totalPages = totalJobs,
                    percentage = 1.0f,
                    currentFileName = primaryName,
                    title = "Selesai mengubah ke gambar"
                )
            )

            AppLogger.d(TAG, "PDF to Image completed successfully: $totalJobs images created, total bytes: $totalOutputBytes")

            val convResult = ConversionResult(
                uri = primaryUri,
                filename = primaryName,
                sizeFormatted = formattedSize,
                pages = totalJobs
            )

            Result.success(
                PdfToImageResult(
                    conversionResult = convResult,
                    outputFilesCount = totalJobs,
                    totalOutputBytes = totalOutputBytes,
                    outputUris = createdOutputUris,
                    imageDimensions = outputDimensions
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "PDF to Image failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            PdfTemporaryFileManager.deleteTempFile(tempSourceFile)
        }
    }

    private fun createDocumentInTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        mimeType: String,
        displayName: String
    ): Uri? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            DocumentsContract.createDocument(contentResolver, parentUri, mimeType, displayName)
        } catch (_: Exception) {
            try {
                DocumentsContract.createDocument(contentResolver, treeUri, mimeType, displayName)
            } catch (_: Exception) {
                null
            }
        }
    }
}
