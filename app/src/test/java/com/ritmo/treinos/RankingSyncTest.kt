package com.ritmo.treinos

import android.content.Context
import androidx.room.Room
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
class RankingSyncTest {
    private lateinit var db:RitmoDatabase
    private lateinit var service:OnlineService
    private lateinit var sync:RankingSynchronizer
    private var failAfterCommit=true
    private var invalid=false
    private var received=0
    private val ledger=mutableSetOf<String>()
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,RitmoDatabase::class.java).allowMainThreadQueries().build()
        var session:String?=null
        service=OnlineService(context,OnlineTransport { path,_,body,_ -> when {
            path.startsWith("/auth/v1/token") -> JSONObject("""{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"A"}}""")
            path.endsWith("ritmo_get_profile") -> JSONObject("""{"nickname":"Alias","ranking_enabled":true}""")
            path.endsWith("ritmo_my_summary") -> JSONObject("""{"total_xp":100,"today_xp":100,"active_days":1,"rewarded_workouts":1}""")
            path.endsWith("ritmo_submit_workout") -> {
                received++
                if(invalid) throw OnlineException(400,"invalid_time","expired")
                ledger.add(body!!.getString("p_event_id"))
                if(failAfterCommit) {failAfterCommit=false;throw java.io.IOException("lost response")}
                JSONObject("""{"xp":100,"duplicate":true}""")
            }
            else -> JSONObject()
        } },{session},{session=it},true)
        sync=RankingSynchronizer(db,service)
    }
    @After fun close() {db.close()}
    @Test fun lostResponseRetriesSameIdAndNeverDuplicatesServerAward():Unit=runBlocking {
        service.login("a@b.test","password"); db.rankingDao().insert(RankingEvent("same-id","A",1_000,61_000))
        assertFalse(sync.sync()); assertEquals(1,db.rankingDao().pending("A").size)
        assertTrue(sync.sync()); assertTrue(db.rankingDao().pending("A").isEmpty()); assertEquals(1,ledger.size); assertEquals(2,received)
    }
    @Test fun anotherAccountAndGuestCannotSendOwnedQueue():Unit=runBlocking {
        service.login("a@b.test","password"); db.rankingDao().insert(RankingEvent("b-id","B",1_000,61_000)); assertTrue(sync.sync());assertEquals(0,received)
        service.logout(); assertTrue(sync.sync()); assertEquals(1,db.rankingDao().pending("B").size)
    }
    @Test fun malformedEventDoesNotRemainInRetryLoop():Unit=runBlocking {
        service.login("a@b.test","password"); db.rankingDao().insert(RankingEvent("invalid","A",1_000,61_000)); invalid=true
        assertTrue(sync.sync()); assertTrue(db.rankingDao().pending("A").isEmpty()); assertEquals(0,ledger.size)
    }
}
