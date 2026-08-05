package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PdfTemporaryFileManager {

    fun createTempPdfFileFromUri(context: Context, contentResolver: ContentResolver, uri: Uri): File? {
        val tempFile = File(context.cacheDir, "temp_merge_${UUID.randomUUID()}.pdf")
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            } ?: return null
            return tempFile
        } catch (_: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            return null
        }
    }

    fun deleteTempFile(file: File?) {
        if (file != null && file.exists()) {
            try {
                file.delete()
            } catch (_: Exception) {}
        }
    }
}
