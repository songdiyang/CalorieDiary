package com.calorie.diary.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    private const val MAX_EDGE = 1280
    private const val PHOTO_DIR = "images"
    private const val PHOTO_TTL_MS = 24 * 60 * 60 * 1000L

    fun loadJpeg(context: Context, uri: Uri): ByteArray {
        val bitmap = loadDisplayBitmap(context, uri)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    /**
     * 解码用于拍照预览的位图：限制长边省内存，并按 EXIF 摆正方向。
     * 相机照片通常以传感器方向（横向）存储，不摆正会横躺/倒置。
     */
    fun loadDisplayBitmap(context: Context, uri: Uri, maxEdge: Int = MAX_EDGE): Bitmap {
        val resolver = context.contentResolver

        // 先读尺寸做 inSampleSize，避免大图 OOM
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageLoadException()

        var sample = 1
        val larger = maxOf(bounds.outWidth, bounds.outHeight)
        while (larger / sample > maxEdge * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw ImageLoadException()

        val scaled = scaleDown(decoded, maxEdge)
        return applyOrientation(context, uri, scaled)
    }

    /**
     * 为系统相机创建新的临时照片文件，顺便清理一天前的旧照片避免缓存无限增长。
     * 目录不可写或 FileProvider 异常时返回 null。
     */
    fun newCameraPhotoUri(context: Context, prefix: String): Uri? = runCatching {
        val dir = File(context.cacheDir, PHOTO_DIR).apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - PHOTO_TTL_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /** 相机返回成功但文件为空（部分机型取消/写入失败）时视为拍照失败。 */
    fun isPhotoValid(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length != 0L } ?: false
    }.getOrDefault(false)

    private fun scaleDown(src: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(src.width, src.height)
        if (edge <= maxEdge) return src
        val ratio = maxEdge.toFloat() / edge
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true
        )
    }

    private fun applyOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
