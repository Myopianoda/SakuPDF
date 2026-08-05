package com.sakupdf.app.domain

import android.graphics.RectF

object PdfMathUtils {

    fun sanitizeFilename(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return "SakuPDF_Document.pdf"

        // Replace illegal characters for filenames
        var sanitized = trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        // Ensure single .pdf extension
        if (sanitized.endsWith(".pdf", ignoreCase = true)) {
            val baseName = sanitized.substring(0, sanitized.length - 4).trimEnd('.', '_')
            if (baseName.isEmpty()) return "SakuPDF_Document.pdf"
            return "$baseName.pdf"
        }

        sanitized = sanitized.trimEnd('.', '_')
        if (sanitized.isEmpty()) return "SakuPDF_Document.pdf"
        return "$sanitized.pdf"
    }

    fun calculatePageSizePoints(
        sizeOption: String,
        imageWidth: Int,
        imageHeight: Int
    ): Pair<Float, Float> {
        val safeW = if (imageWidth <= 0) 1 else imageWidth
        val safeH = if (imageHeight <= 0) 1 else imageHeight

        return when (sizeOption) {
            "A4" -> Pair(595f, 842f)
            "Letter" -> Pair(612f, 792f)
            else -> { // "Otomatis"
                val maxLongEdge = 842f
                if (safeW >= safeH) {
                    val h = maxLongEdge * (safeH.toFloat() / safeW.toFloat())
                    Pair(maxLongEdge, h.coerceAtLeast(100f))
                } else {
                    val w = maxLongEdge * (safeW.toFloat() / safeH.toFloat())
                    Pair(w.coerceAtLeast(100f), maxLongEdge)
                }
            }
        }
    }

    fun applyOrientation(
        pageSize: Pair<Float, Float>,
        orientationOption: String
    ): Pair<Float, Float> {
        val (w, h) = pageSize
        return when (orientationOption) {
            "Potret" -> {
                if (w > h) Pair(h, w) else Pair(w, h)
            }
            "Lanskap" -> {
                if (h > w) Pair(h, w) else Pair(w, h)
            }
            else -> Pair(w, h) // "Otomatis"
        }
    }

    fun calculateMarginPoints(marginOption: String): Float {
        return when (marginOption) {
            "Kecil" -> 24f
            "Sedang" -> 48f
            else -> 0f // "Tanpa margin"
        }
    }

    fun calculateMaxDimension(qualityOption: String): Int {
        return when {
            qualityOption.contains("Hemat", ignoreCase = true) -> 1600
            qualityOption.contains("Tinggi", ignoreCase = true) -> 3200
            else -> 2400 // "Seimbang"
        }
    }

    fun calculateFitCenterRect(
        imageWidth: Int,
        imageHeight: Int,
        containerWidth: Float,
        containerHeight: Float
    ): RectF {
        val safeImgW = if (imageWidth <= 0) 1f else imageWidth.toFloat()
        val safeImgH = if (imageHeight <= 0) 1f else imageHeight.toFloat()
        val safeContW = if (containerWidth <= 0f) 1f else containerWidth
        val safeContH = if (containerHeight <= 0f) 1f else containerHeight

        val imgAspect = safeImgW / safeImgH
        val contAspect = safeContW / safeContH

        val drawWidth: Float
        val drawHeight: Float

        if (imgAspect > contAspect) {
            drawWidth = safeContW
            drawHeight = safeContW / imgAspect
        } else {
            drawHeight = safeContH
            drawWidth = safeContH * imgAspect
        }

        val left = (safeContW - drawWidth) / 2f
        val top = (safeContH - drawHeight) / 2f

        return RectF(left, top, left + drawWidth, top + drawHeight)
    }

    fun calculateProgressPercentage(processed: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
