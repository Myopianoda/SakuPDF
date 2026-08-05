package com.sakupdf.app.model

import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector

data class PdfDocument(
    val id: String,
    val name: String,
    val sizeFormatted: String,
    val dateFormatted: String,
    val pages: Int,
    val isPdf: Boolean = true,
    val location: String = "/storage/emulated/0/Documents/SakuPDF",
    val status: String = "Dokumen dipindai dan diamankan",
    val uri: Uri? = null
)

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String
)

data class ImageItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val rotation: Float = 0f
)

data class PageItem(
    val pageNumber: Int,
    val isSelected: Boolean = false
)

enum class ThemeOption(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap")
}

enum class CompressionLevel(val title: String, val badge: String?, val description: String) {
    HIGH("Kualitas tinggi", null, "Ukuran sedikit lebih kecil, mempertahankan kualitas gambar dan teks agar tetap optimal."),
    BALANCED("Seimbang", "Disarankan", "Ukuran lebih kecil dengan kualitas yang sangat baik untuk penggunaan umum dan email."),
    MINIMUM("Ukuran minimum", null, "Kompresi maksimum. Kualitas gambar mungkin sedikit berkurang, cocok untuk web.")
}

enum class SplitMethod(val title: String, val description: String) {
    ALL("Ekstrak semua halaman", "Jadikan setiap halaman sebagai PDF terpisah"),
    CUSTOM("Rentang halaman khusus", "Pilih halaman spesifik untuk dipisahkan"),
    VISUAL("Pilih visual", "Pilih halaman dari pratinjau thumbnail")
}

data class PdfSettings(
    val filename: String = "",
    val pageSize: String = "Otomatis",
    val orientation: String = "Potret",
    val margin: String = "Tanpa margin",
    val quality: String = "Seimbang (Default)"
)

data class ConversionProgress(
    val processedPages: Int = 0,
    val totalPages: Int = 0,
    val percentage: Float = 0f,
    val currentFileName: String = "",
    val title: String = "Membuat PDF"
)

data class ConversionResult(
    val uri: Uri,
    val filename: String,
    val sizeFormatted: String,
    val pages: Int
)
