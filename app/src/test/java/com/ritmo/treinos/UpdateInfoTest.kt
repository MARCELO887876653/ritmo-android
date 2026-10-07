package com.ritmo.treinos

import com.ritmo.treinos.update.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class UpdateInfoTest {
    private val repo = "owner/ritmo"
    private fun json(code: Int = 2, minimum: Int = 1, force: Boolean = false, url: String = "https://github.com/owner/ritmo/releases/download/v1.1.0/app.apk") = """{"versionCode":$code,"versionName":"1.1.0","minimumVersionCode":$minimum,"forceUpdate":$force,"message":"Nova versão","downloadUrl":"$url"}"""
    @Test fun optionalUpdateCanWait() { val info = UpdateInfo.parse(json(), repo); assertTrue(info.available(1)); assertFalse(info.mandatory(1)) }
    @Test fun explicitForcedUpdateBlocksOldVersion() { assertTrue(UpdateInfo.parse(json(force = true), repo).mandatory(1)) }
    @Test fun minimumSupportedVersionBlocksOldVersion() { assertTrue(UpdateInfo.parse(json(minimum = 2), repo).mandatory(1)) }
    @Test fun installedUpdateNeverBlocks() { val info = UpdateInfo.parse(json(force = true), repo); assertFalse(info.available(2)); assertFalse(info.mandatory(2)); assertFalse(info.mandatory(3)) }
    @Test fun badMetadataIsRejected() {
        listOf("{}", "not json", json(minimum = 3), json(url = "http://github.com/owner/ritmo/releases/download/x/app.apk"), json(url = "https://github.com/attacker/ritmo/releases/download/x/app.apk"), json(url = "https://github.com.evil/owner/ritmo/releases/download/x/app.apk"), json().replace("\"versionCode\":2", "\"versionCode\":2.5"), json().replace("\"forceUpdate\":false", "\"forceUpdate\":\"false\"")).forEach { raw -> assertTrue(raw, runCatching { UpdateInfo.parse(raw, repo) }.isFailure) }
    }
    @Test fun invalidCacheDoesNotInventForcedUpdate() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("test-updates", 0); prefs.edit().clear().putString("updateJson", "broken").commit()
        assertNull(UpdateManager(prefs, repo).cached())
        prefs.edit().putString("updateJson", json(force = true)).commit()
        assertTrue(UpdateManager(prefs, repo).cached()!!.mandatory(1))
    }
    @Test fun offlineTimeoutAndInvalidJsonKeepOnlyValidatedCache() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("offline-updates", 0); prefs.edit().clear().commit()
        val offline = UpdateManager(prefs, repo) { throw java.io.IOException("offline") }
        assertNull(offline.check().info)
        prefs.edit().putString("updateJson", json(force = true)).commit()
        assertTrue(offline.check().info!!.mandatory(1))
        val timeout = UpdateManager(prefs, repo) { throw java.net.SocketTimeoutException("timeout") }
        assertTrue(timeout.check().info!!.mandatory(1))
        assertTrue(UpdateManager(prefs, repo) { "bad json" }.check().info!!.mandatory(1))
        val recovered = UpdateManager(prefs, repo) { json(force = false) }.check()
        assertNull(recovered.error); assertFalse(recovered.info!!.mandatory(1))
    }

}
