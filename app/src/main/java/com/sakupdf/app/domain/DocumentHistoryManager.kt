package com.sakupdf.app.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.sakupdf.app.model.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DocumentHistoryManager {

    private const val TAG = "SakuPDF_HistoryManager"
    private const val HISTORY_FILENAME = "sakupdf_document_history.json"

    fun loadHistory(context: Context): List<PdfDocument> {
        val file = File(context.filesDir, HISTORY_FILENAME)
        if (!file.exists() || file.length() == 0L) {
            return emptyList()
        }

        return try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<PdfDocument>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uriStr = if (obj.has("uri") && !obj.isNull("uri")) obj.getString("uri") else null
                list.add(
                    PdfDocument(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Dokumen.pdf"),
                        sizeFormatted = obj.optString("sizeFormatted", "0 KB"),
                        dateFormatted = obj.optString("dateFormatted", "Hari ini"),
                        pages = obj.optInt("pages", 1),
                        isPdf = obj.optBoolean("isPdf", true),
                        location = obj.optString("location", "Penyimpanan Perangkat"),
                        status = obj.optString("status", "Tersimpan"),
                        uri = if (!uriStr.isNullOrBlank()) Uri.parse(uriStr) else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read history JSON: ${e.message}")
            emptyList()
        }
    }

    fun saveHistory(context: Context, documents: List<PdfDocument>) {
        try {
            val jsonArray = JSONArray()
            for (doc in documents) {
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("name", doc.name)
                    put("sizeFormatted", doc.sizeFormatted)
                    put("dateFormatted", doc.dateFormatted)
                    put("pages", doc.pages)
                    put("isPdf", doc.isPdf)
                    put("location", doc.location)
                    put("status", doc.status)
                    put("uri", doc.uri?.toString())
                }
                jsonArray.put(obj)
            }

            val file = File(context.filesDir, HISTORY_FILENAME)
            val tempFile = File(context.filesDir, "${HISTORY_FILENAME}.tmp")
            tempFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            if (tempFile.exists()) {
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save history JSON: ${e.message}")
        }
    }

    fun addDocument(context: Context, doc: PdfDocument): List<PdfDocument> {
        val current = loadHistory(context).toMutableList()
        // Deduplicate if already present with same uri or id
        current.removeAll { it.id == doc.id || (doc.uri != null && it.uri == doc.uri) }
        current.add(0, doc)
        saveHistory(context, current)
        return current
    }

    fun removeDocument(context: Context, docId: String): List<PdfDocument> {
        val current = loadHistory(context).toMutableList()
        current.removeAll { it.id == docId }
        saveHistory(context, current)
        return current
    }

    fun updateDocument(context: Context, updatedDoc: PdfDocument): List<PdfDocument> {
        val current = loadHistory(context).map {
            if (it.id == updatedDoc.id) updatedDoc else it
        }
        saveHistory(context, current)
        return current
    }

    fun isUriAccessible(contentResolver: ContentResolver, uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            if (uri.scheme == "file") {
                val path = uri.path ?: return false
                File(path).exists()
            } else {
                contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Delete document via SAF DocumentsContract if supported by provider.
     * Always removes from local history even if provider throws UnsupportedOperationException.
     */
    fun deleteDocumentSaf(
        context: Context,
        contentResolver: ContentResolver,
        doc: PdfDocument
    ): Pair<Boolean, List<PdfDocument>> {
        var safDeleted = false
        val uri = doc.uri

        if (uri != null) {
            try {
                safDeleted = DocumentsContract.deleteDocument(contentResolver, uri)
                AppLogger.d(TAG, "DocumentsContract.deleteDocument result: $safDeleted for $uri")
            } catch (e: UnsupportedOperationException) {
                AppLogger.d(TAG, "Provider does not support deleteDocument: ${e.message}")
            } catch (e: SecurityException) {
                AppLogger.d(TAG, "SecurityException on deleteDocument: ${e.message}")
            } catch (e: Throwable) {
                AppLogger.d(TAG, "Error on deleteDocument: ${e.message}")
            }
        }

        val updatedList = removeDocument(context, doc.id)
        return Pair(safDeleted, updatedList)
    }

    /**
     * Rename document via SAF DocumentsContract if supported by provider.
     * Falls back gracefully to updating local display name if provider rejects or does not support rename.
     */
    fun renameDocumentSaf(
        context: Context,
        contentResolver: ContentResolver,
        doc: PdfDocument,
        newName: String
    ): Pair<PdfDocument, List<PdfDocument>> {
        val sanitizedName = if (doc.isPdf) {
            PdfMathUtils.sanitizeFilename(newName)
        } else {
            val raw = newName.trim()
            if (!raw.endsWith(".jpg", ignoreCase = true) && !raw.endsWith(".png", ignoreCase = true)) {
                "$raw.jpg"
            } else raw
        }

        var newUri = doc.uri

        if (doc.uri != null) {
            try {
                val renamedUri = DocumentsContract.renameDocument(contentResolver, doc.uri, sanitizedName)
                if (renamedUri != null) {
                    newUri = renamedUri
                    AppLogger.d(TAG, "DocumentsContract.renameDocument succeeded: newUri=$renamedUri")
                }
            } catch (e: UnsupportedOperationException) {
                AppLogger.d(TAG, "Provider does not support renameDocument: ${e.message}")
            } catch (e: SecurityException) {
                AppLogger.d(TAG, "SecurityException on renameDocument: ${e.message}")
            } catch (e: Throwable) {
                AppLogger.d(TAG, "Error on renameDocument: ${e.message}")
            }
        }

        val updatedDoc = doc.copy(
            name = sanitizedName,
            uri = newUri
        )

        val updatedList = updateDocument(context, updatedDoc)
        return Pair(updatedDoc, updatedList)
    }
}
