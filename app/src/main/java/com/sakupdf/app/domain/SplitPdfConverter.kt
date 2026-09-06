package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.SplitMethod
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

data class SplitJob(
    val outputFilename: String,
    val pages: List<Int> // 1-indexed page numbers
)

data class SplitResult(
    val conversionResult: ConversionResult,
    val outputFilesCount: Int,
    val totalSourceBytes: Long,
    val totalOutputBytes: Long,
    val outputUris: List<Uri>
)

object SplitPdfConverter {

    private const val TAG = "SakuPDF_SplitConverter"

    suspend fun split(
        context: Context,
        contentResolver: ContentResolver,
        sourceUri: Uri,
        sourceName: String,
        splitMethod: SplitMethod,
        customRangeString: String = "",
        selectedVisualPages: List<Int> = emptyList(),
        destinationTreeUri: Uri? = null,
        singleTargetUri: Uri? = null,
        onProgress: (ConversionProgress) -> Unit
    ): Result<SplitResult> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Starting Split PDF for $sourceName using method $splitMethod")

        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Throwable) {}

        // Check for password protection
        if (PdfDocumentInspector.isPdfPasswordProtected(contentResolver, sourceUri)) {
            AppLogger.e(TAG, "Source PDF is password protected.")
            return@withContext Result.failure(Exception("File PDF dilindungi kata sandi dan tidak dapat dipisahkan."))
        }

        val tempSourceFile = PdfTemporaryFileManager.createTempPdfFileFromUri(context, contentResolver, sourceUri)
            ?: run {
                AppLogger.e(TAG, "Failed to copy source PDF to cache.")
                return@withContext Result.failure(Exception("File PDF tidak dapat dibaca atau rusak."))
            }

        var sourceDoc: PDDocument? = null
        val createdOutputUris = mutableListOf<Uri>()
        var totalOutputBytes = 0L

        try {
            sourceDoc = try {
                PDDocument.load(tempSourceFile)
            } catch (e: Exception) {
                AppLogger.e(TAG, "PDDocument.load failed: ${e.message}")
                val msg = e.message ?: ""
                if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                    throw Exception("File PDF dilindungi kata sandi dan tidak dapat dipisahkan.")
                }
                throw Exception("File PDF rusak atau tidak dapat dibuka.")
            }

            if (sourceDoc.isEncrypted) {
                throw Exception("File PDF dilindungi kata sandi dan tidak dapat dipisahkan.")
            }

            val totalPages = sourceDoc.numberOfPages
            if (totalPages <= 0) {
                throw Exception("File PDF kosong atau tidak memiliki halaman.")
            }

            val totalSourceBytes = tempSourceFile.length().coerceAtLeast(1L)
            val baseName = sourceName.substringBeforeLast(".pdf", sourceName).ifBlank { "Dokumen" }
            val sanitizedBase = PdfMathUtils.sanitizeFilename(baseName).removeSuffix(".pdf")

            // Determine split jobs based on splitMethod
            val jobs = mutableListOf<SplitJob>()

            when (splitMethod) {
                SplitMethod.ALL -> {
                    for (p in 1..totalPages) {
                        val fileName = String.format("%s_page_%03d.pdf", sanitizedBase, p)
                        jobs.add(SplitJob(outputFilename = fileName, pages = listOf(p)))
                    }
                }
                SplitMethod.CUSTOM -> {
                    val parseResult = SplitRangeParser.parse(customRangeString, totalPages)
                    when (parseResult) {
                        is SplitRangeResult.Error -> throw Exception(parseResult.message)
                        is SplitRangeResult.Success -> {
                            for (range in parseResult.subRanges) {
                                val fileName = "${sanitizedBase}_${range.toFilenameLabel()}.pdf"
                                jobs.add(SplitJob(outputFilename = fileName, pages = range.pages))
                            }
                        }
                    }
                }
                SplitMethod.VISUAL -> {
                    if (selectedVisualPages.isEmpty()) {
                        throw Exception("Pilih setidaknya 1 halaman untuk dipisahkan.")
                    }
                    val validPages = selectedVisualPages.filter { it in 1..totalPages }.distinct()
                    if (validPages.isEmpty()) {
                        throw Exception("Tidak ada halaman terpilih yang valid.")
                    }
                    for (p in validPages) {
                        val fileName = String.format("%s_page_%03d.pdf", sanitizedBase, p)
                        jobs.add(SplitJob(outputFilename = fileName, pages = listOf(p)))
                    }
                }
            }

            if (jobs.isEmpty()) {
                throw Exception("Tidak ada halaman yang dipilih untuk dipisahkan.")
            }

            val totalJobs = jobs.size
            AppLogger.d(TAG, "Executing ${jobs.size} split jobs.")

            jobs.forEachIndexed { index, job ->
                coroutineContext.ensureActive()

                val currentStep = index + 1
                val progressPercent = currentStep.toFloat() / totalJobs.toFloat()
                onProgress(
                    ConversionProgress(
                        processedPages = index,
                        totalPages = totalJobs,
                        percentage = progressPercent,
                        currentFileName = job.outputFilename,
                        title = "Menyimpan berkas $currentStep dari $totalJobs"
                    )
                )

                // Copy pages without rasterizing
                var targetDoc: PDDocument? = null
                var outputStream: OutputStream? = null
                var writtenBytes = 0L

                try {
                    targetDoc = PDDocument()
                    for (pageNumber in job.pages) {
                        val pageIndex = pageNumber - 1
                        if (pageIndex in 0 until totalPages) {
                            val srcPage = sourceDoc.getPage(pageIndex)
                            targetDoc.importPage(srcPage)
                        }
                    }

                    // Determine output target
                    val outputUri: Uri
                    if (singleTargetUri != null && totalJobs == 1) {
                        outputUri = singleTargetUri
                        outputStream = contentResolver.openOutputStream(outputUri, "w")
                            ?: throw Exception("Gagal membuka lokasi penyimpanan untuk ${job.outputFilename}.")
                    } else if (destinationTreeUri != null) {
                        if (destinationTreeUri.scheme == "file") {
                            val outDir = File(destinationTreeUri.path ?: context.cacheDir.path)
                            outDir.mkdirs()
                            val outFile = File(outDir, job.outputFilename)
                            outputStream = FileOutputStream(outFile)
                            outputUri = Uri.fromFile(outFile)
                        } else {
                            // Storage Access Framework tree document
                            val docUri = createDocumentInTree(contentResolver, destinationTreeUri, "application/pdf", job.outputFilename)
                                ?: throw Exception("Gagal membuat file ${job.outputFilename} di folder tujuan.")
                            outputUri = docUri
                            outputStream = contentResolver.openOutputStream(outputUri, "w")
                                ?: throw Exception("Gagal membuka stream untuk file ${job.outputFilename}.")
                        }
                    } else {
                        // Fallback to internal cache if no tree URI provided
                        val outFile = File(context.cacheDir, job.outputFilename)
                        outputStream = FileOutputStream(outFile)
                        outputUri = Uri.fromFile(outFile)
                    }

                    targetDoc.save(outputStream)
                    outputStream.flush()
                    createdOutputUris.add(outputUri)

                    // Calculate bytes written
                    if (outputUri.scheme == "file") {
                        writtenBytes = File(outputUri.path ?: "").length()
                    } else {
                        try {
                            contentResolver.openFileDescriptor(outputUri, "r")?.use { pfd ->
                                writtenBytes = pfd.statSize
                            }
                        } catch (_: Exception) {}
                    }
                    totalOutputBytes += writtenBytes
                } finally {
                    try { outputStream?.close() } catch (_: Exception) {}
                    try { targetDoc?.close() } catch (_: Exception) {}
                }
            }

            coroutineContext.ensureActive()

            val primaryUri = createdOutputUris.firstOrNull() ?: sourceUri
            val primaryName = if (jobs.size == 1) jobs.first().outputFilename else "${jobs.size} Berkas PDF"
            val formattedSize = if (totalOutputBytes > 0) {
                val kb = totalOutputBytes / 1024f
                if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
            } else {
                "PDF"
            }

            onProgress(
                ConversionProgress(
                    processedPages = totalJobs,
                    totalPages = totalJobs,
                    percentage = 1.0f,
                    currentFileName = primaryName,
                    title = "Selesai memisahkan PDF"
                )
            )

            AppLogger.d(TAG, "Split completed successfully. Created ${jobs.size} files, total bytes: $totalOutputBytes")

            val convResult = ConversionResult(
                uri = primaryUri,
                filename = primaryName,
                sizeFormatted = formattedSize,
                pages = if (jobs.size == 1) jobs.first().pages.size else jobs.size
            )

            Result.success(
                SplitResult(
                    conversionResult = convResult,
                    outputFilesCount = jobs.size,
                    totalSourceBytes = totalSourceBytes,
                    totalOutputBytes = totalOutputBytes,
                    outputUris = createdOutputUris
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Split failed: ${e.message}")
            Result.failure(e)
        } finally {
            try { sourceDoc?.close() } catch (_: Exception) {}
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
