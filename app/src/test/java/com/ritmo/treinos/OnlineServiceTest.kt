package com.ritmo.treinos

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.data.RitmoDatabase
import com.ritmo.treinos.online.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class OnlineServiceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private var stored: String?=null
    private lateinit var service: OnlineService
    private lateinit var fake: Fake
    @Before fun setup() {stored=null; fake=Fake(); service=create()}
    private fun create()=OnlineService(context,fake,{stored},{stored=it},true)
    private suspend fun login() { service.login("me@example.test","long-password"); service.loadProfile() }
    @Test fun loginRefreshLogoutPreserveLocalWorkoutsAndHidePublicEmail(): Unit=runBlocking {
        val db=RitmoDatabase.open(context,"auth-local.db"); db.dao().insertExercise(com.ritmo.treinos.data.Exercise(name="Local"))
        login(); assertEquals("A",service.account.value!!.id); assertEquals("A",service.rankingOwner()); assertEquals(100L,service.summary.value.total)
        service.logout(); assertNull(service.account.value); assertNull(service.rankingOwner()); assertNull(stored)
        assertEquals("Local",db.dao().exercises().single().name); assertTrue(fake.calls.any {it.startsWith("/auth/v1/logout")}); db.close(); context.deleteDatabase("auth-local.db")
    }
    @Test fun confirmedProfilePersistsForOfflineWorkouts(): Unit=runBlocking {
        login(); val reopened=create(); assertEquals("A",reopened.rankingOwner())
        fake.offline=true; assertTrue(runCatching {reopened.loadProfile()}.isFailure); assertEquals("A",reopened.rankingOwner())
    }
    @Test fun signupUsesAuthAndDoesNotPretendEmailIsConfirmed(): Unit=runBlocking {
        service.signup("me@example.test","long-password"); assertNull(service.account.value)
        assertTrue(fake.calls.contains("/auth/v1/signup")); assertTrue(runCatching {service.signup("bad","123")}.isFailure)
    }
    @Test fun expiredSessionRefreshesOnceAndPersistsNewToken(): Unit=runBlocking {
        login(); val j=JSONObject(stored!!).put("expires_at",1); stored=j.toString(); val reopened=create(); reopened.loadProfile()
        assertEquals(1,fake.calls.count {it.contains("refresh_token")}); assertTrue(JSONObject(stored!!).getLong("expires_at")>System.currentTimeMillis()/1000); assertEquals("A",reopened.rankingOwner())
    }
    @Test fun revokedRefreshRequiresLoginButNetworkFailurePreservesSession(): Unit=runBlocking {
        login(); stored=JSONObject(stored!!).put("expires_at",1).toString(); val reopened=create(); fake.offline=true
        assertTrue(runCatching {reopened.loadProfile()}.isFailure); assertNotNull(reopened.account.value)
        fake.offline=false; fake.revoked=true; assertTrue(runCatching {reopened.loadProfile()}.isFailure); assertNull(reopened.account.value)
    }
    @Test fun requestNeverSendsClientXpOrPrivateHistoryAndRefusesAccountMismatch(): Unit=runBlocking {
        login(); val e=RankingEvent("event","A",1_000,61_000)
        assertEquals(100,service.submit(e)); assertEquals(setOf("p_event_id","p_started_at","p_completed_at"),fake.lastBody!!.keys().asSequence().toSet())
        assertTrue(runCatching {service.submit(e.copy(ownerId="B"))}.isFailure)
    }
    @Test fun recoveryUsesPkceAndRejectsUnrequestedLink(): Unit=runBlocking {
        context.getSharedPreferences("ritmo_ranking_cache",0).edit().clear().commit()
        assertTrue(runCatching {service.handleRecovery(Uri.parse("ritmo://auth/recovery?code=bad"))}.isFailure)
        service.recover("me@example.test"); assertTrue(fake.lastBody!!.getString("code_challenge").isNotBlank()); assertEquals("s256",fake.lastBody!!.getString("code_challenge_method"))
        service.handleRecovery(Uri.parse("ritmo://auth/recovery?code=valid")); assertTrue(service.recovery.value); assertNull(service.rankingOwner())
        service.resetPassword("new-password"); assertFalse(service.recovery.value); assertTrue(fake.calls.contains("/auth/v1/user"))
    }
    @Test fun deletingAccountRequiresConfirmedBackendResponse(): Unit=runBlocking {
        login(); fake.deleteFails=true; assertTrue(runCatching {service.deleteAccount()}.isFailure); assertNotNull(service.account.value)
        fake.deleteFails=false; assertEquals("A",service.deleteAccount()); assertNull(service.account.value)
    }
    @Test fun cachedBoardIsScopedToAccountAndDoesNotMixPeriods(): Unit=runBlocking {
        login(); val board=service.board("weekly",10); assertEquals("PublicAlias",board.entries.single().nickname); assertEquals(1L,board.me!!.position)
        assertNotNull(service.cachedBoard("weekly",10)); assertNull(service.cachedBoard("monthly",10)); service.logout(); assertNull(service.cachedBoard("weekly",10))
    }
    private class Fake : OnlineTransport {
        val calls=mutableListOf<String>(); var lastBody: JSONObject?=null; var offline=false; var revoked=false; var deleteFails=false
        override suspend fun request(path: String,method: String,body: JSONObject?,token: String?): JSONObject {
            calls+=path; lastBody=body
            if(offline) throw java.io.IOException("offline")
            if(path.contains("refresh_token") && revoked) throw OnlineException(400,"invalid_grant","expired")
            return when {
                path.startsWith("/auth/v1/token") -> JSONObject("""{"access_token":"test","refresh_token":"refresh","expires_in":3600,"user":{"id":"A","email":"me@example.test"}}""")
                path.endsWith("ritmo_get_profile") || path.endsWith("ritmo_save_profile") -> JSONObject("""{"nickname":"PublicAlias","ranking_enabled":true}""")
                path.endsWith("ritmo_my_summary") -> JSONObject("""{"total_xp":100,"today_xp":100,"active_days":1,"rewarded_workouts":1}""")
                path.endsWith("ritmo_submit_workout") -> JSONObject("""{"xp":100,"total_xp":100,"duplicate":false}""")
                path.endsWith("ritmo_ranking") -> JSONObject("""{"entries":[{"position":1,"nickname":"PublicAlias","xp":100,"level":1}],"me":{"position":1,"nickname":"PublicAlias","xp":100,"level":1},"updated_at":"now"}""")
                path.endsWith("delete-account") -> if(deleteFails) throw java.io.IOException("offline") else JSONObject("""{"deleted":true}""")
                else -> JSONObject()
            }
        }
    }
}
