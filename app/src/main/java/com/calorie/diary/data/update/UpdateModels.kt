package com.calorie.diary.data.update

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** 服务端 version.json 解析后的版本信息。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val sizeBytes: Long,
    val sizeLabel: String,
    val downloadUrl: String,
    val sha256: String?,
    val releaseNotes: List<String>,
    val releasedAt: String?
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

enum class UpdateError { NETWORK, DOWNLOAD, CHECKSUM, APK_INVALID }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateState {
        val percent: Int?
            get() = if (totalBytes > 0L) {
                ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
    }
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState
    data class Failed(val info: UpdateInfo?, val error: UpdateError) : UpdateState
}

/** 只接受 HTTPS 下载地址与有效 versionCode，防止被降级或跳转到明文渠道。 */
internal fun parseUpdateManifest(json: String): UpdateInfo? = try {
    val obj = JSONObject(json)
    val downloadUrl = obj.optString("downloadUrl").trim()
    val versionCode = obj.optInt("versionCode", 0)
    val versionName = obj.optString("version").ifBlank { obj.optString("versionName") }.trim()
    if (!downloadUrl.startsWith("https://") || versionCode <= 0 || versionName.isEmpty()) {
        null
    } else {
        val notes = obj.optJSONArray("releaseNotes")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        UpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            sizeBytes = obj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
            sizeLabel = obj.optString("size").trim(),
            downloadUrl = downloadUrl,
            sha256 = obj.optString("sha256").trim().takeIf { it.isNotEmpty() },
            releaseNotes = notes,
            releasedAt = obj.optString("releasedAt").trim().takeIf { it.isNotEmpty() }
        )
    }
} catch (e: Exception) {
    null
}

internal fun formatUpdateSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}