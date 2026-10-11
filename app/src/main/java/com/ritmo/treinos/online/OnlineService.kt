package com.ritmo.treinos.online

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.ritmo.treinos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

fun interface OnlineTransport {
    suspend fun request(path: String, method: String, body: JSONObject?, token: String?): JSONObject
}
class SupabaseHttp(private val base: String, private val publicKey: String) : OnlineTransport {
    override suspend fun request(path: String, method: String, body: JSONObject?, token: String?): JSONObject = withContext(Dispatchers.IO) {
        require(base.startsWith("https://") && URL(base).host.endsWith(".supabase.co")) { "Backend não configurado." }
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000; connection.readTimeout = 20000; connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("apikey", publicKey)
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) } }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = input?.use { stream ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = stream.read(buffer); if (n < 0) break; require(out.size() + n <= 1024*1024); out.write(buffer,0,n) }
                out.toString("UTF-8")
            }.orEmpty()
            val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrElse { throw OnlineException(status,"invalid_response","O servidor retornou uma resposta inválida.") }
            if (status !in 200..299) {
                val code = json.optString("error_code",json.optString("code", "http_$status"))
                val known = listOf("authentication_required","session_revoked","profile_required","ranking_disabled","invalid_time","invalid_event","overlapping_event")
                val serverCode = known.firstOrNull { json.optString("message").contains(it) } ?: code
                throw OnlineException(status,serverCode, when(serverCode) {
                    "invalid_credentials" -> "E-mail ou senha incorretos."
                    "email_not_confirmed" -> "Confirme seu e-mail antes de entrar."
                    "23505" -> "Este apelido já está em uso."
                    "invalid_time" -> "Treino fora da janela de sincronização ou com horários inválidos."
                    "overlapping_event" -> "Este treino se sobrepõe a outro registro online."
                    "ranking_disabled" -> "A participação no ranking está desativada."
                    "session_revoked", "authentication_required" -> "Entre novamente para sincronizar."
                    else -> if (status == 429) "Muitas tentativas. Aguarde e tente novamente." else if (status >= 500) "Servidor indisponível. Tente mais tarde." else "Não foi possível concluir. Confira os dados e tente novamente."
                })
            }
            json
        } finally { connection.disconnect() }
    }
}

class OnlineService(context: Context, private val transport: OnlineTransport = SupabaseHttp(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY),
    private val readSecret: (() -> String?)? = null, private val writeSecret: ((String?) -> Unit)? = null,
    val configured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
    private val vault by lazy { SessionVault(context) }
    private val prefs = context.getSharedPreferences("ritmo_ranking_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var session: JSONObject? = runCatching { (readSecret?.invoke() ?: if(readSecret==null) vault.read() else null)?.let(::JSONObject) }.getOrNull()
    val account = MutableStateFlow(session?.account())
    val profile = MutableStateFlow<OnlineProfile?>(session?.optJSONObject("profile")?.let { OnlineProfile(it.getString("nickname"),it.getBoolean("enabled")) })
    val summary = MutableStateFlow(session?.optJSONObject("summary")?.let { XpSummary(it.optLong("total"),it.optLong("today"),it.optLong("days"),it.optLong("workouts")) } ?: XpSummary())
    val recovery = MutableStateFlow(session?.optBoolean("recovery",false)==true)
    val syncError = MutableStateFlow<String?>(null)
    fun rankingOwner(): String? = account.value?.id?.takeIf { profile.value?.enabled == true && !recovery.value }
    private fun JSONObject.account(): Account? = optJSONObject("user")?.let { Account(it.getString("id"),it.optString("email")) }
    private fun saveSession(value: JSONObject?) {
        // Ritmo uses Supabase sessions only; Google API tokens and profile metadata are unnecessary.
        value?.remove("provider_token"); value?.remove("provider_refresh_token")
        value?.optJSONObject("user")?.let { user -> value.put("user",JSONObject().put("id",user.getString("id")).put("email",user.optString("email"))) }
        if(value!=null && !value.has("expires_at")) value.put("expires_at",System.currentTimeMillis()/1000 + value.optLong("expires_in",3600))
        if(writeSecret!=null) writeSecret.invoke(value?.toString()) else if(value==null) vault.clear() else vault.write(value.toString())
        session=value; account.value=value?.account(); recovery.value=value?.optBoolean("recovery",false)==true
        if(value==null) { profile.value=null; summary.value=XpSummary() }
    }
    private fun persistProfile() {
        session?.let { value ->
            profile.value?.let { p -> value.put("profile",JSONObject().put("nickname",p.nickname).put("enabled",p.enabled)) } ?: value.remove("profile")
            saveSession(value)
        }
    }
    private fun requireConfigured() { check(configured) { "O ranking ainda precisa da configuração do Supabase. Seus treinos offline continuam disponíveis." } }
    private suspend fun access(): String {
        requireConfigured(); val old=checkNotNull(session) { "Entre na sua conta." }
        if(old.optLong("expires_at") <= System.currentTimeMillis()/1000 + 60) {
            try {
                val fresh=transport.request("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",old.getString("refresh_token")),null)
                fresh.put("recovery",recovery.value); old.optJSONObject("profile")?.let { fresh.put("profile",it) }; old.optJSONObject("summary")?.let { fresh.put("summary",it) }; saveSession(fresh)
            } catch(e: OnlineException) { if(e.status in listOf(400,401,403)) saveSession(null); throw e }
        }
        return checkNotNull(session).getString("access_token")
    }
    suspend fun login(email: String,password: String) = mutex.withLock {
        requireConfigured(); require(email.contains('@') && password.isNotBlank()) { "Informe e-mail e senha." }
        val value=transport.request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email.trim()).put("password",password),null)
        clearAuthRequests(); profile.value=null; summary.value=XpSummary(); saveSession(value)
    }
    suspend fun signup(email: String,password: String) = mutex.withLock {
        requireConfigured(); require(email.contains('@') && password.length>=8) { "Use um e-mail válido e uma senha com pelo menos 8 caracteres." }
        val challenge=prepareAuthRequest("confirm")
        val value=transport.request("/auth/v1/signup?redirect_to=ritmo%3A%2F%2Fauth%2Fconfirm","POST",JSONObject().put("email",email.trim()).put("password",password).put("code_challenge",challenge).put("code_challenge_method","s256"),null)
        if(value.has("access_token")) { clearAuthRequests(); profile.value=null; summary.value=XpSummary(); saveSession(value) }
    }
    private fun prepareAuthRequest(flow: String): String {
        val verifier=Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) },Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        // Verifier has no bearer authority. Private, backup-excluded preferences survive process death.
        check(prefs.edit().putString("${flow}_verifier",verifier).putLong("${flow}_requested",System.currentTimeMillis()).commit()) { "Não foi possível iniciar o acesso. Tente novamente." }
        return challenge
    }
    private fun clearAuthRequests() {
        val edit=prefs.edit()
        listOf("confirm","google","recovery").forEach { edit.remove("${it}_verifier").remove("${it}_requested") }
        edit.commit()
    }
    fun acceptsAuthLink(uri: Uri): Boolean = uri.scheme=="ritmo" && uri.host=="auth" && uri.port==-1 && uri.userInfo==null && uri.path in listOf("/confirm","/google","/recovery")
    suspend fun beginGoogleLogin(): Uri = mutex.withLock {
        requireConfigured()
        val settings=transport.request("/auth/v1/settings","GET",null,null)
        check(settings.optJSONObject("external")?.optBoolean("google")==true) { "O login com Google precisa ser habilitado no Supabase pelo responsável pelo Ritmo. Você pode entrar com e-mail e senha." }
        val base=Uri.parse(BuildConfig.SUPABASE_URL)
        require(base.scheme=="https" && base.host?.endsWith(".supabase.co")==true && base.userInfo==null && base.port==-1) { "Endereço de autenticação inválido." }
        base.buildUpon().path("/auth/v1/authorize").clearQuery().fragment(null)
            .appendQueryParameter("provider","google")
            .appendQueryParameter("redirect_to","ritmo://auth/google")
            .appendQueryParameter("scopes","openid email profile")
            .appendQueryParameter("code_challenge",prepareAuthRequest("google"))
            .appendQueryParameter("code_challenge_method","s256")
            .appendQueryParameter("prompt","select_account").build()
    }
    private fun rejectAuthError(uri: Uri) {
        val fragment=uri.fragment?.let { Uri.parse("ritmo://auth/error?$it") }
        val error=uri.getQueryParameter("error") ?: fragment?.getQueryParameter("error")
        if(error!=null) throw IllegalArgumentException(if(error=="access_denied") "A entrada foi cancelada. Você pode tentar novamente." else "Link de acesso inválido ou expirado. Solicite outro pelo app.")
    }
    private suspend fun exchangeAuthCode(uri: Uri, flow: String, timeout: Long): Boolean {
        rejectAuthError(uri)
        val code=uri.getQueryParameter("code")?.takeIf { it.isNotBlank() && it.length<=4096 } ?: return false
        val verifier=prefs.getString("${flow}_verifier",null)
        require(verifier!=null && System.currentTimeMillis()-prefs.getLong("${flow}_requested",0) in 0..timeout) { "Inicie o acesso neste aparelho e abra o link dentro do prazo. Se já confirmou seu e-mail, entre com e-mail e senha." }
        val value=transport.request("/auth/v1/token?grant_type=pkce","POST",JSONObject().put("auth_code",code).put("code_verifier",verifier),null)
        require(value.optString("access_token").isNotBlank() && value.optJSONObject("user")?.optString("id")?.isNotBlank()==true) { "Não foi possível validar sua sessão. Tente novamente." }
        clearAuthRequests(); profile.value=null; summary.value=XpSummary(); value.put("recovery",flow=="recovery"); saveSession(value)
        return true
    }
    suspend fun handleAuthLink(uri: Uri): Boolean = mutex.withLock {
        if(!acceptsAuthLink(uri)) return@withLock false
        requireConfigured()
        val flow=uri.path!!.removePrefix("/")
        val result=exchangeAuthCode(uri,flow,if(flow=="confirm") 86_400_000L else 600_000L)
        require(result || flow=="confirm") { "Link de acesso inválido. Inicie a entrada novamente pelo app." }
        result
    }
    suspend fun recover(email: String) = mutex.withLock {
        requireConfigured(); require(email.contains('@')) { "Informe seu e-mail." }
        val challenge=prepareAuthRequest("recovery")
        transport.request("/auth/v1/recover?redirect_to=ritmo%3A%2F%2Fauth%2Frecovery","POST",JSONObject().put("email",email.trim()).put("code_challenge",challenge).put("code_challenge_method","s256"),null)
    }
    suspend fun handleRecovery(uri: Uri) { if(uri.path=="/recovery") handleAuthLink(uri) }
    suspend fun resetPassword(password: String) = mutex.withLock {
        require(recovery.value && password.length>=8) { "Use uma senha de pelo menos 8 caracteres." }
        transport.request("/auth/v1/user","PUT",JSONObject().put("password",password),access())
        session?.let { it.put("recovery",false); saveSession(it) }
    }
    suspend fun logout() = mutex.withLock {
        val token=session?.optString("access_token")
        // Local logout succeeds offline; no account-local workout data is removed.
        saveSession(null); clearAuthRequests()
        if(token!=null) runCatching { transport.request("/auth/v1/logout?scope=local","POST",JSONObject(),token) }
        Unit
    }
    suspend fun loadProfile() = mutex.withLock {
        val token=access()
        val json=transport.request("/rest/v1/rpc/ritmo_get_profile","POST",JSONObject(),token)
        profile.value=if(json.optString("nickname").isBlank()) null else OnlineProfile(json.getString("nickname"),json.getBoolean("ranking_enabled"))
        persistProfile()
        val j=transport.request("/rest/v1/rpc/ritmo_my_summary","POST",JSONObject(),token)
        summary.value=XpSummary(j.getLong("total_xp"),j.getLong("today_xp"),j.getLong("active_days"),j.getLong("rewarded_workouts"))
        session?.let { it.put("summary",JSONObject().put("total",summary.value.total).put("today",summary.value.today).put("days",summary.value.activeDays).put("workouts",summary.value.workouts)); saveSession(it) }
    }
    suspend fun saveProfile(nickname: String, enabled: Boolean) = mutex.withLock {
        require(Regex("[A-Za-z0-9_]{3,24}").matches(nickname)) { "Apelido: 3 a 24 letras, números ou _." }
        val j=transport.request("/rest/v1/rpc/ritmo_save_profile","POST",JSONObject().put("p_nickname",nickname).put("p_enabled",enabled),access())
        profile.value=OnlineProfile(j.getString("nickname"),j.getBoolean("ranking_enabled")); persistProfile()
    }
    suspend fun board(period: String, limit: Int): RankingBoard = mutex.withLock {
        requireConfigured(); require(period in listOf("weekly","monthly","all") && limit in listOf(10,100))
        val j=transport.request("/rest/v1/rpc/ritmo_ranking","POST",JSONObject().put("p_period",period).put("p_limit",limit),if(account.value==null) null else access())
        prefs.edit().putString("board_${account.value?.id ?: "guest"}_${period}_$limit",j.toString()).apply()
        parseBoard(j)
    }
    fun cachedBoard(period: String,limit: Int): RankingBoard? = runCatching { prefs.getString("board_${account.value?.id ?: "guest"}_${period}_$limit",null)?.let { parseBoard(JSONObject(it)) } }.getOrNull()
    private fun parseBoard(j: JSONObject): RankingBoard {
        fun row(o: JSONObject)=RankingEntry(o.getLong("position"),o.getString("nickname"),o.getLong("xp"),o.getLong("level"))
        val list=j.getJSONArray("entries")
        return RankingBoard(List(list.length()) { row(list.getJSONObject(it)) },j.optJSONObject("me")?.let(::row),j.getString("updated_at"))
    }
    suspend fun submit(event: RankingEvent): Int = mutex.withLock {
        check(account.value?.id==event.ownerId) { "Esta pendência pertence a outra conta." }
        val j=transport.request("/rest/v1/rpc/ritmo_submit_workout","POST",JSONObject().put("p_event_id",event.eventId)
            .put("p_started_at",Instant.ofEpochMilli(event.startedAt).toString()).put("p_completed_at",Instant.ofEpochMilli(event.completedAt).toString()),access())
        j.getInt("xp")
    }
    suspend fun deleteAccount(): String = mutex.withLock {
        val owner=checkNotNull(account.value).id
        val result=transport.request("/functions/v1/delete-account","POST",JSONObject(),access())
        check(result.optBoolean("deleted")) { "A exclusão não foi confirmada. Tente novamente." }
        saveSession(null); prefs.edit().clear().apply(); owner
    }
}
