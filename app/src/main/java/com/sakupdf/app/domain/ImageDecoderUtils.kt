package com.sakupdf.app.domain

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.max

object ImageDecoderUtils {

    fun getExifOrientationDegrees(contentResolver: ContentResolver, uri: Uri): Float {
        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri) ?: return 0f
            val exif = ExifInterface(inputStream)
            return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            return 0f
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    fun decodeDownsampledBitmap(
        contentResolver: ContentResolver,
        uri: Uri,
        maxDimension: Int,
        userRotationDegrees: Float
    ): Bitmap? {
        val exifDegrees = getExifOrientationDegrees(contentResolver, uri)
        val totalRotation = (exifDegrees + userRotationDegrees) % 360f

        var bitmap: Bitmap? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val origW = info.size.width
                    val origH = info.size.height
                    val maxSide = max(origW, origH)
                    if (maxSide > maxDimension && maxDimension > 0) {
                        val sampleSize = maxSide / maxDimension
                        decoder.setTargetSampleSize(sampleSize.coerceAtLeast(1))
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }

        // Fallback for API 26-27 or if ImageDecoder failed
        if (bitmap == null) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                val origW = options.outWidth
                val origH = options.outHeight

                var sampleSize = 1
                if (origW > 0 && origH > 0) {
                    val maxSide = max(origW, origH)
                    if (maxSide > maxDimension && maxDimension > 0) {
                        sampleSize = maxSide / maxDimension
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }

        val baseBitmap = bitmap ?: return null

        // Apply total rotation matrix if needed
        if (totalRotation != 0f) {
            try {
                val matrix = Matrix().apply { postRotate(totalRotation) }
                val rotated = Bitmap.createBitmap(
                    baseBitmap,
                    0,
                    0,
                    baseBitmap.width,
                    baseBitmap.height,
                    matrix,
                    true
                )
                if (rotated != baseBitmap) {
                    baseBitmap.recycle()
                }
                return rotated
            } catch (_: Exception) {
                return baseBitmap
            }
        }

        return baseBitmap
    }
}
