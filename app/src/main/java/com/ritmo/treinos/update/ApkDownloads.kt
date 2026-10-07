package com.ritmo.treinos.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.ritmo.treinos.BuildConfig
import java.io.File
import java.security.MessageDigest

enum class DownloadPhase { IDLE, DOWNLOADING, PAUSED, READY, FAILED }
fun UpdateInfo.sameApk(other: UpdateInfo): Boolean = versionCode == other.versionCode && versionName == other.versionName && downloadUrl == other.downloadUrl
data class ApkDownloadState(val phase: DownloadPhase = DownloadPhase.IDLE, val info: UpdateInfo? = null, val downloaded: Long = 0, val total: Long = -1, val error: String? = null) {
    val progress: Float? get() = if (total > 0) (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
}
data class DownloadRecord(val status: Int, val downloaded: Long, val total: Long, val reason: Int = 0)
interface DownloadBackend {
    fun enqueue(info: UpdateInfo): Long
    fun query(id: Long): DownloadRecord?
    fun file(info: UpdateInfo): File
    fun remove(id: Long)
}
class AndroidDownloadBackend(private val context: Context) : DownloadBackend {
    private val manager = context.getSystemService(DownloadManager::class.java)
    override fun file(info: UpdateInfo): File {
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) { "Armazenamento indisponível. Tente novamente." }
        return File(directory, "updates/ritmo-${info.versionCode}.apk")
    }
    override fun enqueue(info: UpdateInfo): Long {
        val target = file(info)
        require(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory) { "Não foi possível preparar o download." }
        require(!target.exists() || target.delete()) { "Não foi possível substituir o download anterior." }
        val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("Ritmo ${info.versionName}").setDescription("Baixando atualização do Ritmo")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/ritmo-${info.versionCode}.apk")
        return manager.enqueue(request)
    }
    override fun query(id: Long): DownloadRecord? = manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
        if (!c.moveToFirst()) return@use null
        DownloadRecord(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
    }
    override fun remove(id: Long) { manager.remove(id) }
}
data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String?, val signers: Set<String>)
fun verifyUpdateIdentity(installed: ApkIdentity, archive: ApkIdentity, expected: UpdateInfo) {
    require(archive.packageName == installed.packageName) { "O arquivo baixado não é uma atualização do Ritmo." }
    require(archive.versionCode == expected.versionCode.toLong() && archive.versionCode > installed.versionCode && archive.versionName == expected.versionName) { "A versão do APK não corresponde à atualização anunciada." }
    require(installed.signers.isNotEmpty() && archive.signers == installed.signers) { "A assinatura do APK não corresponde ao Ritmo instalado." }
}
fun interface ApkVerifier { fun verify(file: File, info: UpdateInfo) }
class AndroidApkVerifier(private val context: Context) : ApkVerifier {
    @Suppress("DEPRECATION")
    override fun verify(file: File, info: UpdateInfo) {
        require(file.isFile && file.length() in 1..MAX_APK_BYTES) { "O download está incompleto ou inválido. Tente novamente." }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageInfo(context.packageName, flags)
        val archive = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageArchiveInfo(file.absolutePath, flags)
        requireNotNull(archive) { "O arquivo recebido não é um APK válido." }
        verifyUpdateIdentity(identity(installed), identity(archive), info)
    }
    @Suppress("DEPRECATION")
    private fun identity(p: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        val signers = signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        return ApkIdentity(p.packageName, PackageInfoCompat.getLongVersionCode(p), p.versionName, signers)
    }
}
const val MAX_APK_BYTES = 200L * 1024 * 1024
data class InstallAction(val intent: Intent, val needsPermission: Boolean)
class UpdateFileProvider : FileProvider()

/** DownloadManager owns the background transfer; only update metadata is persisted here. */
class ApkDownloads(private val context: Context, private val prefs: SharedPreferences,
    private val backend: DownloadBackend = AndroidDownloadBackend(context),
    private val verifier: ApkVerifier = AndroidApkVerifier(context)) {
    private var verified: Triple<Long, Long, Long>? = null
    private fun info(): UpdateInfo? = runCatching { UpdateInfo.parse(prefs.getString("apkDownloadInfo", "") ?: "", BuildConfig.GITHUB_REPOSITORY) }.getOrNull()
    private fun id() = prefs.getLong("apkDownloadId", 0)
    @Synchronized fun start(value: UpdateInfo): ApkDownloadState {
        val valid = UpdateInfo.parse(value.toJson(), BuildConfig.GITHUB_REPOSITORY)
        require(valid.available(BuildConfig.VERSION_CODE)) { "Esta atualização já está instalada." }
        val previous = refresh()
        if (previous.info?.sameApk(valid) == true && previous.phase in listOf(DownloadPhase.DOWNLOADING, DownloadPhase.PAUSED, DownloadPhase.READY)) return previous
        cancel()
        return try {
            val downloadId = backend.enqueue(valid)
            require(downloadId > 0) { "Não foi possível iniciar o download." }
            prefs.edit().putLong("apkDownloadId", downloadId).putString("apkDownloadInfo", valid.toJson()).remove("apkDownloadError").commit()
            ApkDownloadState(DownloadPhase.DOWNLOADING, valid)
        } catch (e: Exception) {
            prefs.edit().putString("apkDownloadInfo", valid.toJson()).putString("apkDownloadError", e.message ?: "Não foi possível iniciar o download.").commit()
            ApkDownloadState(DownloadPhase.FAILED, valid, error = prefs.getString("apkDownloadError", null))
        }
    }
    @Synchronized fun refresh(): ApkDownloadState {
        val value = info()
        if (value == null || !value.available(BuildConfig.VERSION_CODE)) {
            if (id() > 0 || prefs.contains("apkDownloadInfo") || prefs.contains("apkDownloadError")) cancel()
            return ApkDownloadState()
        }
        prefs.getString("apkDownloadError", null)?.let { return ApkDownloadState(DownloadPhase.FAILED, value, error = it) }
        val downloadId = id()
        if (downloadId <= 0) return ApkDownloadState()
        return try {
            val record = backend.query(downloadId) ?: error("O download não foi encontrado. Tente novamente.")
            require(record.downloaded <= MAX_APK_BYTES && record.total <= MAX_APK_BYTES) { "O arquivo recebido é muito grande. Download cancelado." }
            val phase = when (record.status) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> DownloadPhase.DOWNLOADING
                DownloadManager.STATUS_PAUSED -> DownloadPhase.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = backend.file(value)
                    val key = Triple(downloadId, file.lastModified(), file.length())
                    if (verified != key) { verifier.verify(file, value); verified = key }
                    DownloadPhase.READY
                }
                DownloadManager.STATUS_FAILED -> error(if (record.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) "Sem espaço para baixar a atualização. Libere espaço e tente novamente." else "Não foi possível baixar o APK. Verifique sua conexão e tente novamente.")
                else -> error("Estado do download indisponível. Tente novamente.")
            }
            ApkDownloadState(phase, value, record.downloaded, record.total)
        } catch (e: Exception) {
            runCatching { backend.remove(downloadId) }
            verified = null
            val message = e.message ?: "Não foi possível verificar o APK. Tente novamente."
            prefs.edit().remove("apkDownloadId").putString("apkDownloadError", message).commit()
            ApkDownloadState(DownloadPhase.FAILED, value, error = message)
        }
    }
    @Synchronized fun cancel() {
        if (id() > 0) runCatching { backend.remove(id()) }
        verified = null
        prefs.edit().remove("apkDownloadId").remove("apkDownloadInfo").remove("apkDownloadError").commit()
    }
    @Synchronized fun installAction(expected: UpdateInfo): InstallAction {
        val current = refresh()
        require(current.phase == DownloadPhase.READY && current.info?.sameApk(expected) == true) { "Baixe a atualização anunciada antes de instalar." }
        val file = backend.file(expected)
        verifier.verify(file, expected) // Recheck just before handing the file to Android.
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallAction(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")), true)
        }
        // Hand Android a verified private copy so shared-storage changes cannot replace it.
        val directory = File(context.cacheDir, "updates")
        require(directory.mkdirs() || directory.isDirectory) { "Não foi possível preparar a instalação." }
        val staged = File.createTempFile("ritmo-", ".apk", directory)
        val ready = File(directory, "ritmo-${expected.versionCode}.apk")
        try {
            file.copyTo(staged, overwrite = true)
            verifier.verify(staged, expected)
            require((!ready.exists() || ready.delete()) && staged.renameTo(ready)) { "Não foi possível preparar o APK para instalação." }
        } finally { staged.delete() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("Atualização do Ritmo", uri)
        return InstallAction(intent, false)
    }
}
