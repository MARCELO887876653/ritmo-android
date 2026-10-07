package com.ritmo.treinos

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.update.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class ApkDownloadsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("apk-download-tests", Context.MODE_PRIVATE)
    private val info get() = UpdateInfo(BuildConfig.VERSION_CODE + 1, "1.1.0", 1, false, "Atualização", "https://github.com/${BuildConfig.GITHUB_REPOSITORY}/releases/download/v1.1.0/ritmo-1.1.0.apk")
    private lateinit var backend: FakeBackend
    private var rejectFile = false
    private var verifications = 0
    private fun manager() = ApkDownloads(context, prefs, backend, ApkVerifier { file, _ ->
        verifications++; require(file.isFile && !rejectFile) { "Assinatura inválida" }
    })
    @Before fun prepare() { prefs.edit().clear().commit(); backend = FakeBackend(context); rejectFile = false; verifications = 0 }
    @After fun cleanup() { prefs.edit().clear().commit() }
    @Test fun downloadRestoresAcrossRecreationAndResumesAfterOfflinePause() {
        val first = manager()
        assertEquals(DownloadPhase.DOWNLOADING, first.start(info).phase)
        first.start(info); assertEquals(1, backend.enqueues)
        backend.record = DownloadRecord(DownloadManager.STATUS_RUNNING, 50, 100)
        val restored = manager()
        assertEquals(0.5f, restored.refresh().progress)
        backend.record = DownloadRecord(DownloadManager.STATUS_PAUSED, 50, 100, DownloadManager.PAUSED_WAITING_FOR_NETWORK)
        assertEquals(DownloadPhase.PAUSED, restored.refresh().phase)
        backend.record = DownloadRecord(DownloadManager.STATUS_SUCCESSFUL, 100, 100)
        assertEquals(DownloadPhase.READY, restored.refresh().phase)
        restored.refresh(); assertEquals(1, verifications)
    }
    @Test fun failedDownloadOffersRetryAndRespectsSizeLimit() {
        val downloads = manager(); downloads.start(info)
        backend.record = DownloadRecord(DownloadManager.STATUS_FAILED, 0, -1, DownloadManager.ERROR_INSUFFICIENT_SPACE)
        assertTrue(downloads.refresh().error!!.contains("Sem espaço"))
        assertEquals(DownloadPhase.FAILED, manager().refresh().phase)
        assertEquals(DownloadPhase.DOWNLOADING, downloads.start(info).phase)
        backend.record = DownloadRecord(DownloadManager.STATUS_RUNNING, MAX_APK_BYTES + 1, -1)
        assertEquals(DownloadPhase.FAILED, downloads.refresh().phase)
        assertTrue(backend.live.isEmpty())
    }
    @Test fun cancelDoesNotEraseCachedMandatoryUpdateOrAppPreferences() {
        prefs.edit().putString("updateJson", info.copy(forceUpdate = true).toJson()).putString("theme", "dark").commit()
        val downloads = manager(); downloads.start(info); downloads.cancel()
        assertEquals(DownloadPhase.IDLE, manager().refresh().phase)
        assertTrue(backend.live.isEmpty()); assertEquals("dark", prefs.getString("theme", null))
        assertTrue(UpdateManager(prefs).cached()!!.mandatory(BuildConfig.VERSION_CODE))
    }
    @Test fun installerUsesSystemPermissionAndScopedContentUri() {
        val downloads = manager(); downloads.start(info)
        backend.record = DownloadRecord(DownloadManager.STATUS_SUCCESSFUL, 100, 100)
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        val permission = downloads.installAction(info)
        assertTrue(permission.needsPermission)
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permission.intent.action)
        assertEquals("package:${context.packageName}", permission.intent.data.toString())
        shadowOf(context.packageManager).setCanRequestPackageInstalls(true)
        val installer = downloads.installAction(info)
        assertFalse(installer.needsPermission)
        assertEquals(Intent.ACTION_VIEW, installer.intent.action)
        assertEquals("application/vnd.android.package-archive", installer.intent.type)
        assertEquals("content", installer.intent.data!!.scheme)
        assertEquals("${context.packageName}.updates", installer.intent.data!!.authority)
        assertTrue(installer.intent.data!!.path!!.startsWith("/verified_updates/"))
        context.contentResolver.openInputStream(installer.intent.data!!)!!.use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        assertTrue(installer.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(installer.intent.clipData)
        assertTrue(verifications >= 3)
    }
    @Test fun invalidDownloadedApkNeverBecomesInstallable() {
        val downloads = manager(); downloads.start(info); rejectFile = true
        backend.record = DownloadRecord(DownloadManager.STATUS_SUCCESSFUL, 100, 100)
        assertEquals(DownloadPhase.FAILED, downloads.refresh().phase)
        assertTrue(runCatching { downloads.installAction(info) }.isFailure)
    }
    @Test fun apkIdentityRequiresSamePackageNewVersionAndSameCertificate() {
        val installed = ApkIdentity(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, setOf("real-certificate"))
        val valid = installed.copy(versionCode = info.versionCode.toLong(), versionName = info.versionName)
        verifyUpdateIdentity(installed, valid, info)
        listOf(valid.copy(packageName = "other.app"), valid.copy(versionCode = installed.versionCode),
            valid.copy(versionName = "0.0.0"), valid.copy(signers = setOf("other-certificate")), valid.copy(signers = emptySet()))
            .forEach { assertTrue(runCatching { verifyUpdateIdentity(installed, it, info) }.isFailure) }
    }
    @Test fun alreadyInstalledDownloadIsCleanedUpWithoutDeletingUpdateCache() {
        val downloads = manager(); downloads.start(info)
        prefs.edit().putString("apkDownloadInfo", info.copy(versionCode = BuildConfig.VERSION_CODE).toJson()).putString("updateJson", info.toJson()).commit()
        assertEquals(DownloadPhase.IDLE, downloads.refresh().phase)
        assertTrue(backend.live.isEmpty()); assertNotNull(UpdateManager(prefs).cached())
    }
    @Test fun untrustedUrlDoesNotReplaceActiveDownload() {
        val downloads = manager(); downloads.start(info)
        assertTrue(runCatching { downloads.start(info.copy(downloadUrl = "https://example.com/app.apk")) }.isFailure)
        assertEquals(1, backend.enqueues); assertEquals(DownloadPhase.DOWNLOADING, downloads.refresh().phase)
    }
    @Test fun installMustMatchTheCurrentlyAnnouncedUpdate() {
        val downloads = manager(); downloads.start(info)
        backend.record = DownloadRecord(DownloadManager.STATUS_SUCCESSFUL, 100, 100)
        assertTrue(runCatching { downloads.installAction(info.copy(versionCode = info.versionCode + 1)) }.isFailure)
    }
    @Test fun updatedReleaseMessageDoesNotDiscardTheSameDownloadedApk() {
        val downloads = manager(); downloads.start(info)
        backend.record = DownloadRecord(DownloadManager.STATUS_SUCCESSFUL, 100, 100)
        val revised = info.copy(message = "Mensagem revisada", forceUpdate = true)
        assertEquals(DownloadPhase.READY, downloads.start(revised).phase)
        assertEquals(1, backend.enqueues)
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        assertTrue(downloads.installAction(revised).needsPermission)
    }
    private class FakeBackend(private val context: Context) : DownloadBackend {
        var record = DownloadRecord(DownloadManager.STATUS_PENDING, 0, -1)
        val live = mutableSetOf<Long>(); var enqueues = 0
        override fun enqueue(info: UpdateInfo): Long {
            enqueues++; val id = enqueues.toLong(); live += id
            record = DownloadRecord(DownloadManager.STATUS_PENDING, 0, -1)
            file(info).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            return id
        }
        override fun query(id: Long) = record.takeIf { id in live }
        override fun file(info: UpdateInfo) = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/test-${info.versionCode}.apk")
        override fun remove(id: Long) { live -= id }
    }
}
