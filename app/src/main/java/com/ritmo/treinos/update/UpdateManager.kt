package com.ritmo.treinos.update

import android.content.SharedPreferences
import com.ritmo.treinos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Only validated metadata is cached. A network failure never invents a mandatory update. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val minimumVersionCode: Int, val forceUpdate: Boolean, val message: String, val downloadUrl: String) {
    fun toJson(): String = JSONObject().put("versionCode", versionCode).put("versionName", versionName).put("minimumVersionCode", minimumVersionCode).put("forceUpdate", forceUpdate).put("message", message).put("downloadUrl", downloadUrl).toString()
    fun available(installed: Int) = versionCode > installed
    fun mandatory(installed: Int) = available(installed) && (forceUpdate || installed < minimumVersionCode)
    companion object {
        fun parse(raw: String, repository: String): UpdateInfo {
            require(raw.toByteArray().size <= 65536) { "JSON de atualização muito grande." }
            val j = JSONObject(raw)
            require(j.get("versionCode") is Number && j.get("minimumVersionCode") is Number && j.get("forceUpdate") is Boolean)
            val code = j.getInt("versionCode"); val minimum = j.getInt("minimumVersionCode")
            require(j.getDouble("versionCode") == code.toDouble() && j.getDouble("minimumVersionCode") == minimum.toDouble())
            val name = j.getString("versionName"); val message = j.getString("message"); val url = j.getString("downloadUrl")
            val uri = URI(url)
            require(code > 0 && minimum in 1..code && name.isNotBlank() && name.length <= 80 && message.length <= 2000)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.startsWith("/$repository/releases/download/") && uri.path.endsWith(".apk") && !uri.path.contains("..")) { "O APK precisa estar nos Releases oficiais." }
            return UpdateInfo(code, name, minimum, j.getBoolean("forceUpdate"), message, url)
        }
    }
}
data class UpdateCheck(val info: UpdateInfo?, val error: String? = null)
class UpdateManager(private val prefs: SharedPreferences, private val repository: String = BuildConfig.GITHUB_REPOSITORY, private val fetchOverride: (() -> String)? = null) {
    fun cached(): UpdateInfo? = runCatching { UpdateInfo.parse(prefs.getString("updateJson", "") ?: "", repository) }.getOrNull()
    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val raw = fetchOverride?.invoke() ?: fetch()
            val info = UpdateInfo.parse(raw, repository)
            prefs.edit().putString("updateJson", raw).apply()
            UpdateCheck(info)
        } catch (_: Exception) { UpdateCheck(cached(), "Não foi possível consultar o GitHub. Seus treinos continuam disponíveis offline.") }
    }
    private fun fetch(): String {
            val url = URI("https://raw.githubusercontent.com/$repository/main/version.json").toURL()
            val c = url.openConnection() as HttpsURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000; c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("Cache-Control", "no-cache")
            val raw = try {
                require(c.responseCode == 200) { "GitHub indisponível (${c.responseCode})." }
                c.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 65536); output.write(buffer, 0, count) }
                    output.toString("UTF-8")
                }
            } finally { c.disconnect() }
            return raw
    }
}
