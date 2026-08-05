package com.sakupdf.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakupdf.app.model.CompressionLevel
import com.sakupdf.app.model.ImageItem
import com.sakupdf.app.model.PageItem
import com.sakupdf.app.model.PdfDocument
import com.sakupdf.app.model.SplitMethod
import com.sakupdf.app.model.ThemeOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SakuPDFUiState(
    val documents: List<PdfDocument> = initialDummyDocuments,
    val selectedFilter: String = "Semua",
    val activeDocument: PdfDocument? = initialDummyDocuments.firstOrNull(),
    val imagesToConvert: List<ImageItem> = initialDummyImages,
    val pdfsToMerge: List<PdfDocument> = initialDummyMergePdfs,
    val splitMethod: SplitMethod = SplitMethod.ALL,
    val splitCustomRange: String = "1-5, 8, 11-14",
    val splitPageItems: List<PageItem> = (1..6).map { PageItem(it, it % 2 != 0) },
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    val pdfToImageFormat: String = "JPG",
    val pdfToImageQuality: String = "Tinggi",
    val pdfToImageSelectAll: Boolean = false,
    val pdfToImagePages: List<PageItem> = (1..6).map { PageItem(it, it in listOf(1, 3, 4)) },
    val pdfExportName: String = "SakuPDF_2026-08-05",
    val pdfPageSize: String = "Otomatis",
    val pdfOrientation: String = "Potret",
    val pdfMargin: String = "Tanpa margin",
    val pdfQuality: String = "Seimbang (Default)",
    val processingProgress: Float = 0.65f,
    val processingPageText: String = "Memproses halaman 8 dari 12",
    val processingTitle: String = "Membuat PDF",
    val lastGeneratedDocument: PdfDocument = PdfDocument(
        id = "result_1",
        name = "SakuPDF_2026-08-05.pdf",
        sizeFormatted = "1.2 MB",
        dateFormatted = "Hari ini",
        pages = 4
    ),
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val storageLocation: String = "/storage/emulated/0/Documents/SakuPDF",
    val isAutoBackupEnabled: Boolean = false,
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

val initialDummyImages = listOf(
    ImageItem("img1", "Gambar_001.jpg"),
    ImageItem("img2", "Gambar_002.jpg"),
    ImageItem("img3", "Gambar_003.jpg"),
    ImageItem("img4", "Gambar_004.jpg")
)

val initialDummyMergePdfs = listOf(
    PdfDocument("m1", "Laporan_Keuangan_Q3_2023.pdf", "1.2 MB", "Hari ini", 12),
    PdfDocument("m2", "Lampiran_Bukti_Transaksi.pdf", "845 KB", "Hari ini", 5),
    PdfDocument("m3", "Ringkasan_Eksekutif_Final.pdf", "2.1 MB", "Hari ini", 11)
)

class SakuPDFViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SakuPDFUiState())
    val uiState: StateFlow<SakuPDFUiState> = _uiState.asStateFlow()

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

    fun setPdfExportName(name: String) {
        _uiState.update { it.copy(pdfExportName = name) }
    }

    fun setPdfPageSize(size: String) {
        _uiState.update { it.copy(pdfPageSize = size) }
    }

    fun setPdfOrientation(orientation: String) {
        _uiState.update { it.copy(pdfOrientation = orientation) }
    }

    fun setPdfMargin(margin: String) {
        _uiState.update { it.copy(pdfMargin = margin) }
    }

    fun setPdfQuality(quality: String) {
        _uiState.update { it.copy(pdfQuality = quality) }
    }

    fun setThemeOption(option: ThemeOption) {
        _uiState.update { it.copy(themeOption = option) }
    }

    fun setAutoBackup(enabled: Boolean) {
        _uiState.update { it.copy(isAutoBackupEnabled = enabled) }
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

    fun startProcessing(title: String, onComplete: () -> Unit) {
        _uiState.update { it.copy(processingTitle = title, processingProgress = 0.1f) }
        viewModelScope.launch {
            for (i in 1..10) {
                delay(200)
                _uiState.update { state ->
                    val nextProgress = (i / 10f)
                    state.copy(
                        processingProgress = nextProgress,
                        processingPageText = "Memproses step $i dari 10"
                    )
                }
            }
            onComplete()
        }
    }
}
