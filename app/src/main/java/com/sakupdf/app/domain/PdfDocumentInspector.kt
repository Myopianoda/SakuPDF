package com.sakupdf.app.domain

import android.content.ContentResolver
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.InputStream

object PdfDocumentInspector {

    fun getRealPdfPageCount(contentResolver: ContentResolver, uri: Uri): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = contentResolver.openFileDescriptor(uri, "r") ?: return 0
            renderer = PdfRenderer(pfd)
            return renderer.pageCount
        } catch (_: Exception) {
            return 0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun isPdfPasswordProtected(contentResolver: ContentResolver, uri: Uri): Boolean {
        var inputStream: InputStream? = null
        var pdDoc: PDDocument? = null
        try {
            inputStream = contentResolver.openInputStream(uri) ?: return false
            pdDoc = PDDocument.load(inputStream)
            return pdDoc.isEncrypted
        } catch (e: InvalidPasswordException) {
            return true
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                return true
            }
            return false
        } finally {
            try { pdDoc?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    fun queryPdfFileDetails(contentResolver: ContentResolver, uri: Uri): Pair<String, String> {
        var name: String? = null
        var sizeBytes: Long = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {}

        if (name == null) {
            name = uri.lastPathSegment ?: "Dokumen.pdf"
        }

        if (sizeBytes <= 0L) {
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    sizeBytes = pfd.statSize
                }
            } catch (_: Exception) {}
        }

        val formattedSize = if (sizeBytes > 0) {
            val kb = sizeBytes / 1024f
            if (kb >= 1024) String.format("%.1f MB", kb / 1024f) else String.format("%.0f KB", kb)
        } else {
            "PDF"
        }

        return Pair(name ?: "Dokumen.pdf", formattedSize)
    }
}
