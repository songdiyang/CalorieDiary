package com.calorie.diary.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.calorie.diary.BuildConfig
import com.calorie.diary.R
import com.calorie.diary.data.UiMessenger
import com.calorie.diary.data.network.HttpClients
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

/**
 * 应用内更新：拉取 version.json，比对 versionCode，下载 APK 并交给系统安装器。
 * 状态跑在进程级单例里，弹窗关闭或页面切换都不会中断下载。
 */
object AppUpdater {

    const val MANIFEST_URL = "https://caloriediary.aetherstudio.cn/version.json"
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val PREFS_NAME = "app_update"
    private const val KEY_MANIFEST = "manifest_json"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val DOWNLOAD_DIR = "updates"
    private const val PROGRESS_STEP = 256 * 1024L
    private val AUTO_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var pendingInstall = false

    /**
     * 自动检查命中本地缓存（6 小时内）时直接回放结果，避免每次启动都请求；
     * 手动检查始终走网络。
     */
    fun check(context: Context, manual: Boolean = false) {
        val previous = _state.value
        if (previous is UpdateState.Checking || previous is UpdateState.Downloading) return
        val appContext = context.applicationContext
        _state.value = UpdateState.Checking
        scope.launch {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(KEY_MANIFEST, null)
            val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
            val cacheFresh = System.currentTimeMillis() - checkedAt < AUTO_CHECK_INTERVAL_MS
            if (!manual && cachedJson != null && cacheFresh) {
                applyManifest(cachedJson, appContext, previous)
                return@launch
            }
            val fetched = try {
                fetchManifest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (fetched != null && parseUpdateManifest(fetched) != null) {
                prefs.edit()
                    .putString(KEY_MANIFEST, fetched)
                    .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                    .apply()
                applyManifest(fetched, appContext, previous)
            } else {
                val cachedInfo = cachedJson?.let(::parseUpdateManifest)
                _state.value = when {
                    cachedInfo != null && cachedInfo.isNewerThan(BuildConfig.VERSION_CODE) ->
                        UpdateState.Available(cachedInfo)
                    previous is UpdateState.Ready -> previous
                    manual -> UpdateState.Failed(null, UpdateError.NETWORK)
                    else -> UpdateState.Idle
                }
            }
        }
    }

    private fun fetchManifest(): String {
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("Cache-Control", "no-cache")
            .build()
        HttpClients.sync.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty manifest body")
            return body.string()
        }
    }

    private fun applyManifest(json: String, context: Context, previous: UpdateState) {
        val info = parseUpdateManifest(json)
        if (info == null) {
            _state.value = if (previous is UpdateState.Ready) {
                previous
            } else {
                UpdateState.Failed(null, UpdateError.NETWORK)
            }
            return
        }
        if (!info.isNewerThan(BuildConfig.VERSION_CODE)) {
            cleanupDownloads(context)
            _state.value = UpdateState.UpToDate
            return
        }
        val readySameVersion = previous is UpdateState.Ready &&
            previous.info.versionCode == info.versionCode &&
            previous.apk.exists()
        val downloaded = apkFile(context, info.versionCode)
        val cachedShaMatches = {
            info.sha256.isNullOrBlank() || runCatching {
                info.sha256.equals(sha256Hex(downloaded), ignoreCase = true)
            }.getOrDefault(false)
        }
        // 进程重启后复用已校验过的安装包，避免重复下载
        val readyFromCache = !readySameVersion && downloaded.exists() &&
            downloaded.length() > 0L &&
            (info.sizeBytes <= 0L || downloaded.length() == info.sizeBytes) &&
            cachedShaMatches()
        _state.value = when {
            readySameVersion -> previous
            readyFromCache -> UpdateState.Ready(info, downloaded)
            else -> UpdateState.Available(info)
        }
    }

    fun download(context: Context, info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        val appContext = context.applicationContext
        downloadJob = scope.launch {
            val dir = File(appContext.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = apkFile(appContext, info.versionCode)
            // 独立临时文件：取消/重试不会误删下一轮下载的数据
            val temp = File.createTempFile("update-", ".part", dir)
            _state.value = UpdateState.Downloading(info, 0L, info.sizeBytes)
            try {
                HttpClients.sync.newCall(Request.Builder().url(info.downloadUrl).build())
                    .execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("empty apk body")
                        val total = info.sizeBytes.takeIf { it > 0L }
                            ?: body.contentLength().takeIf { it > 0L }
                            ?: 0L
                        body.byteStream().use { input ->
                            FileOutputStream(temp).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var written = 0L
                                var lastReported = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    written += read
                                    if (written - lastReported >= PROGRESS_STEP) {
                                        lastReported = written
                                        _state.value =
                                            UpdateState.Downloading(info, written, total)
                                    }
                                }
                                _state.value = UpdateState.Downloading(info, written, total)
                            }
                        }
                    }
                if (info.sizeBytes > 0L && temp.length() != info.sizeBytes) {
                    throw IOException("apk size mismatch")
                }
                val expectedSha = info.sha256
                if (!expectedSha.isNullOrBlank() &&
                    !expectedSha.equals(sha256Hex(temp), ignoreCase = true)
                ) {
                    temp.delete()
                    _state.value = UpdateState.Failed(info, UpdateError.CHECKSUM)
                    return@launch
                }
                // 防止清单被篡改后指向其他应用或错误版本
                val archive = appContext.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
                val archiveCode = archive?.let { PackageInfoCompat.getLongVersionCode(it) }
                if (archive?.packageName != appContext.packageName ||
                    archiveCode != info.versionCode.toLong()
                ) {
                    temp.delete()
                    _state.value = UpdateState.Failed(info, UpdateError.APK_INVALID)
                    return@launch
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                _state.value = UpdateState.Ready(info, target)
            } catch (e: CancellationException) {
                temp.delete()
                throw e
            } catch (e: Exception) {
                temp.delete()
                _state.value = UpdateState.Failed(info, UpdateError.DOWNLOAD)
            }
        }
    }

    fun cancelDownload() {
        val job = downloadJob
        downloadJob = null
        job?.cancel()
        // 取消后任务可能刚好完成并进入 Ready，此时保留 Ready 结果
        val current = _state.value
        if (current is UpdateState.Downloading) {
            _state.value = UpdateState.Available(current.info)
        }
    }

    /** 未授予“安装未知应用”时先跳系统设置，返回前台后自动继续。 */
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        val appContext = context.applicationContext
        if (!ready.apk.exists()) {
            _state.value = UpdateState.Failed(ready.info, UpdateError.DOWNLOAD)
            UiMessenger.show(appContext.getString(R.string.update_apk_missing))
            return
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opened = runCatching { appContext.startActivity(intent) }.isSuccess
            pendingInstall = opened
            UiMessenger.show(
                appContext.getString(
                    if (opened) R.string.update_install_permission
                    else R.string.update_install_unavailable
                )
            )
            return
        }
        launchInstaller(appContext, ready.apk)
    }

    fun onForeground(context: Context) {
        if (!pendingInstall) return
        val appContext = context.applicationContext
        if (!appContext.packageManager.canRequestPackageInstalls()) return
        pendingInstall = false
        val ready = _state.value as? UpdateState.Ready ?: return
        if (ready.apk.exists()) {
            launchInstaller(appContext, ready.apk)
        } else {
            _state.value = UpdateState.Failed(ready.info, UpdateError.DOWNLOAD)
            UiMessenger.show(appContext.getString(R.string.update_apk_missing))
        }
    }

    private fun launchInstaller(context: Context, apk: File) {
        val opened = runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
        if (!opened) {
            UiMessenger.show(context.getString(R.string.update_install_unavailable))
        }
    }

    private fun cleanupDownloads(context: Context) {
        File(context.cacheDir, DOWNLOAD_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun apkFile(context: Context, versionCode: Int): File =
        File(File(context.cacheDir, DOWNLOAD_DIR), "CalorieDiary-$versionCode.apk")
}