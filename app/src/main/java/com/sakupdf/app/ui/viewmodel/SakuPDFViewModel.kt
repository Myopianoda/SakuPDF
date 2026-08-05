package com.sakupdf.app.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakupdf.app.domain.AppLogger
import com.sakupdf.app.domain.ImageToPdfConverter
import com.sakupdf.app.domain.PdfMathUtils
import com.sakupdf.app.model.CompressionLevel
import com.sakupdf.app.model.ConversionProgress
import com.sakupdf.app.model.ConversionResult
import com.sakupdf.app.model.ImageItem
import com.sakupdf.app.model.PageItem
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.model.PdfSettings
import com.sakupdf.app.model.SplitMethod
import com.sakupdf.app.model.ThemeOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SakuPDFUiState(
    val documents: List<PdfDocument> = initialDummyDocuments,
    val selectedFilter: String = "Semua",
    val activeDocument: PdfDocument? = initialDummyDocuments.firstOrNull(),
    val imagesToConvert: List<ImageItem> = emptyList(),
    val pdfSettings: PdfSettings = PdfSettings(),
    val isClearAllImagesDialogVisible: Boolean = false,
    val pdfsToMerge: List<PdfDocument> = initialDummyMergePdfs,
    val splitMethod: SplitMethod = SplitMethod.ALL,
    val splitCustomRange: String = "1-5, 8, 11-14",
    val splitPageItems: List<PageItem> = (1..6).map { PageItem(it, it % 2 != 0) },
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    val pdfToImageFormat: String = "JPG",
    val pdfToImageQuality: String = "Tinggi",
    val pdfToImageSelectAll: Boolean = false,
    val pdfToImagePages: List<PageItem> = (1..6).map { PageItem(it, it in listOf(1, 3, 4)) },
    val conversionProgress: ConversionProgress = ConversionProgress(),
    val isProcessing: Boolean = false,
    val isCancelConfirmDialogVisible: Boolean = false,
    val conversionResult: ConversionResult? = null,
    val shouldNavigateToSuccess: Boolean = false,
    val lastGeneratedDocument: PdfDocument = initialDummyDocuments.first(),
    val errorMessage: String? = null,
    val userNotificationMessage: String? = null,
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val storageLocation: String = "/storage/emulated/0/Documents/SakuPDF",
    val deleteCandidate: PdfDocument? = null
)

val initialDummyDocuments = listOf(
    PdfDocument("1", "KTP_Scan.pdf", "245 KB", "Hari ini", 1),
    PdfDocument("2", "Invoice_Agustus_2023.pdf", "1.2 MB", "Kemarin", 3),
    PdfDocument("3", "Kontrak_Kerja_Draft_Final.pdf", "850 KB", "12 Agt", 5),
    PdfDocument("4", "Laporan_Tahunan_2023.pdf", "2.4 MB", "12 Okt 2023", 24),
    PdfDocument("5", "KTP_Scan_Depan.jpg", "845 KB", "10 Okt 2023", 1, isPdf = false),
    PdfDocument("6", "Kontrak_Kerja_Sewa.pdf", "1.1 MB", "05 Okt 2023", 12),
    PdfDocument("7", "Invoice_Desain_UI.pdf", "450 KB", "01 Okt 2023", 2)
)

val initialDummyMergePdfs = listOf(
    PdfDocument("m1", "Laporan_Keuangan_Q3_2023.pdf", "1.2 MB", "Hari ini", 12),
    PdfDocument("m2", "Lampiran_Bukti_Transaksi.pdf", "845 KB", "Hari ini", 5),
    PdfDocument("m3", "Ringkasan_Eksekutif_Final.pdf", "2.1 MB", "Hari ini", 11)
)

class SakuPDFViewModel : ViewModel() {

    private val TAG = "SakuPDF_ViewModel"

    private val _uiState = MutableStateFlow(SakuPDFUiState())
    val uiState: StateFlow<SakuPDFUiState> = _uiState.asStateFlow()

    private var activeConversionJob: Job? = null
    private var activeTargetUri: Uri? = null

    init {
        generateDefaultPdfFilename()
    }

    private fun generateDefaultPdfFilename() {
        val defaultName = try {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            "SakuPDF_${sdf.format(Date())}.pdf"
        } catch (_: Throwable) {
            "SakuPDF_Document.pdf"
        }
        _uiState.update { state ->
            if (state.pdfSettings.filename.isBlank()) {
                state.copy(pdfSettings = state.pdfSettings.copy(filename = defaultName))
            } else state
        }
    }

    fun addImagesFromUris(contentResolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val currentList = _uiState.value.imagesToConvert
        val incomingItems = uris.map { uri ->
            val displayName = queryUriDisplayName(contentResolver, uri) ?: "Gambar.jpg"
            ImageItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = displayName,
                rotation = 0f
            )
        }

        val (newUniqueItems, wasTruncated) = PdfMathUtils.filterAndCapImageItems(
            existingItems = currentList,
            incomingItems = incomingItems,
            getUriKey = { it.uri.toString() },
            maxAllowed = 30
        )

        val updatedList = currentList + newUniqueItems

        _uiState.update { state ->
            state.copy(
                imagesToConvert = updatedList,
                userNotificationMessage = if (wasTruncated) "Maksimal 30 gambar. Gambar lainnya tidak ditambahkan." else null
            )
        }

        generateDefaultPdfFilename()
    }

    fun clearNotificationMessage() {
        _uiState.update { it.copy(userNotificationMessage = null) }
    }

    private fun queryUriDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        return cursor.getString(nameIdx)
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment
    }

    fun rotateImage(id: String) {
        _uiState.update { state ->
            val updated = state.imagesToConvert.map {
                if (it.id == id) it.copy(rotation = (it.rotation + 90f) % 360f) else it
            }
            state.copy(imagesToConvert = updated)
        }
    }

    fun deleteImage(id: String) {
        _uiState.update { state ->
            val updated = state.imagesToConvert.filter { it.id != id }
            state.copy(imagesToConvert = updated)
        }
    }

    fun moveImageUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.imagesToConvert.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            state.copy(imagesToConvert = list)
        }
    }

    fun moveImageDown(index: Int) {
        val currentSize = _uiState.value.imagesToConvert.size
        if (index >= currentSize - 1) return
        _uiState.update { state ->
            val list = state.imagesToConvert.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            state.copy(imagesToConvert = list)
        }
    }

    fun requestClearAllImages() {
        _uiState.update { it.copy(isClearAllImagesDialogVisible = true) }
    }

    fun confirmClearAllImages() {
        _uiState.update { it.copy(imagesToConvert = emptyList(), isClearAllImagesDialogVisible = false) }
    }

    fun dismissClearAllImages() {
        _uiState.update { it.copy(isClearAllImagesDialogVisible = false) }
    }

    fun setPdfExportName(name: String) {
        val sanitized = PdfMathUtils.sanitizeFilename(name)
        _uiState.update { state ->
            state.copy(pdfSettings = state.pdfSettings.copy(filename = sanitized))
        }
    }

    fun setPdfPageSize(size: String) {
        _uiState.update { state ->
            state.copy(pdfSettings = state.pdfSettings.copy(pageSize = size))
        }
    }

    fun setPdfOrientation(orientation: String) {
        _uiState.update { state ->
            state.copy(pdfSettings = state.pdfSettings.copy(orientation = orientation))
        }
    }

    fun setPdfMargin(margin: String) {
        _uiState.update { state ->
            state.copy(pdfSettings = state.pdfSettings.copy(margin = margin))
        }
    }

    fun setPdfQuality(quality: String) {
        _uiState.update { state ->
            state.copy(pdfSettings = state.pdfSettings.copy(quality = quality))
        }
    }

    fun startRealImageToPdfConversion(
        contentResolver: ContentResolver,
        targetUri: Uri,
        onNavigateToProcessing: () -> Unit
    ) {
        val images = _uiState.value.imagesToConvert
        if (images.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Pilih setidaknya 1 gambar untuk dibuat PDF.") }
            return
        }

        AppLogger.d(TAG, "startRealImageToPdfConversion called for ${images.size} images.")

        activeTargetUri = targetUri
        _uiState.update {
            it.copy(
                isProcessing = true,
                errorMessage = null,
                conversionResult = null,
                shouldNavigateToSuccess = false,
                conversionProgress = ConversionProgress(
                    processedPages = 0,
                    totalPages = images.size,
                    percentage = 0f,
                    currentFileName = images.first().name,
                    title = "Membuat PDF"
                )
            )
        }

        onNavigateToProcessing()

        activeConversionJob = viewModelScope.launch {
            val settings = _uiState.value.pdfSettings
            val result = ImageToPdfConverter.convert(
                contentResolver = contentResolver,
                images = images,
                settings = settings,
                targetUri = targetUri,
                onProgress = { progress ->
                    _uiState.update { it.copy(conversionProgress = progress) }
                }
            )

            result.fold(
                onSuccess = { res ->
                    AppLogger.d(TAG, "Conversion succeeded in ViewModel. Emitting success state.")
                    val newDoc = PdfDocument(
                        id = UUID.randomUUID().toString(),
                        name = res.filename,
                        sizeFormatted = res.sizeFormatted,
                        dateFormatted = "Hari ini",
                        pages = res.pages,
                        uri = res.uri
                    )
                    _uiState.update { state ->
                        state.copy(
                            isProcessing = false,
                            conversionResult = res,
                            lastGeneratedDocument = newDoc,
                            activeDocument = newDoc,
                            documents = listOf(newDoc) + state.documents,
                            shouldNavigateToSuccess = true
                        )
                    }
                },
                onFailure = { err ->
                    AppLogger.e(TAG, "Conversion failed in ViewModel: ${err.message}")
                    _uiState.update { state ->
                        state.copy(
                            isProcessing = false,
                            shouldNavigateToSuccess = false,
                            errorMessage = err.localizedMessage ?: "Gagal membuat PDF. Silakan coba lagi."
                        )
                    }
                }
            )
        }
    }

    fun simulateProcessingToSuccessState(result: ConversionResult) {
        _uiState.update { state ->
            state.copy(
                isProcessing = false,
                conversionResult = result,
                shouldNavigateToSuccess = true
            )
        }
    }

    fun onNavigationToSuccessHandled() {
        AppLogger.d(TAG, "Result navigation triggered & handled.")
        _uiState.update { it.copy(shouldNavigateToSuccess = false) }
    }

    fun startDummyProcessing(title: String, onComplete: () -> Unit) {
        _uiState.update {
            it.copy(
                isProcessing = true,
                shouldNavigateToSuccess = false,
                conversionProgress = ConversionProgress(0, 10, 0f, "file_dummy.pdf", title)
            )
        }
        viewModelScope.launch {
            for (i in 1..10) {
                delay(150)
                _uiState.update { state ->
                    val nextProgress = (i / 10f)
                    state.copy(
                        conversionProgress = state.conversionProgress.copy(
                            processedPages = i,
                            totalPages = 10,
                            percentage = nextProgress
                        )
                    )
                }
            }
            _uiState.update { it.copy(isProcessing = false, shouldNavigateToSuccess = true) }
            onComplete()
        }
    }

    fun requestCancelConversion() {
        _uiState.update { it.copy(isCancelConfirmDialogVisible = true) }
    }

    fun confirmCancelConversion(contentResolver: ContentResolver, onCancelled: () -> Unit) {
        AppLogger.d(TAG, "Conversion cancellation requested.")
        activeConversionJob?.cancel()
        activeConversionJob = null

        val targetUri = activeTargetUri
        if (targetUri != null) {
            try {
                DocumentsContract.deleteDocument(contentResolver, targetUri)
            } catch (_: Exception) {}
        }

        _uiState.update {
            it.copy(
                isProcessing = false,
                isCancelConfirmDialogVisible = false,
                shouldNavigateToSuccess = false,
                errorMessage = "Proses pembuatan PDF dibatalkan."
            )
        }
        onCancelled()
    }

    fun dismissCancelConversion() {
        _uiState.update { it.copy(isCancelConfirmDialogVisible = false) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearImageToPdfState() {
        _uiState.update { state ->
            state.copy(
                imagesToConvert = emptyList(),
                conversionResult = null,
                shouldNavigateToSuccess = false,
                pdfSettings = PdfSettings()
            )
        }
        generateDefaultPdfFilename()
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun selectDocument(doc: PdfDocument) {
        _uiState.update { it.copy(activeDocument = doc) }
    }

    fun setSplitMethod(method: SplitMethod) {
        _uiState.update { it.copy(splitMethod = method) }
    }

    fun setSplitCustomRange(range: String) {
        _uiState.update { it.copy(splitCustomRange = range) }
    }

    fun toggleSplitPage(pageNumber: Int) {
        _uiState.update { state ->
            val updated = state.splitPageItems.map {
                if (it.pageNumber == pageNumber) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(splitPageItems = updated)
        }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _uiState.update { it.copy(compressionLevel = level) }
    }

    fun setPdfToImageFormat(format: String) {
        _uiState.update { it.copy(pdfToImageFormat = format) }
    }

    fun setPdfToImageQuality(quality: String) {
        _uiState.update { it.copy(pdfToImageQuality = quality) }
    }

    fun togglePdfToImagePage(pageNumber: Int) {
        _uiState.update { state ->
            val updated = state.pdfToImagePages.map {
                if (it.pageNumber == pageNumber) it.copy(isSelected = !it.isSelected) else it
            }
            state.copy(pdfToImagePages = updated)
        }
    }

    fun setThemeOption(option: ThemeOption) {
        _uiState.update { it.copy(themeOption = option) }
    }

    fun requestDelete(doc: PdfDocument) {
        _uiState.update { it.copy(deleteCandidate = doc) }
    }

    fun confirmDelete() {
        _uiState.update { state ->
            val candidate = state.deleteCandidate
            if (candidate != null) {
                val updatedDocs = state.documents.filter { it.id != candidate.id }
                state.copy(documents = updatedDocs, deleteCandidate = null)
            } else state
        }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteCandidate = null) }
    }
}
