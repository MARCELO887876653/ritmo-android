package com.ritmo.treinos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.data.*
import com.ritmo.treinos.online.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class RankingQueueTest {
    private lateinit var db: RitmoDatabase
    private lateinit var repo: WorkoutRepository
    private var owner: String?=null
    private var time=1_000_000L
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),RitmoDatabase::class.java).allowMainThreadQueries().build(); repo=WorkoutRepository(db,{owner},{time}) }
    @After fun close() {db.close()}
    private suspend fun start(): Long { val e=repo.saveExercise(null,"Supino",""); return repo.start(repo.saveTemplate(null,"A",listOf(e))) }
    private suspend fun finish(id: Long) { val set=db.dao().sets().last(); repo.saveSet(set.id,30.0,10,true); repo.finish(id) }
    @Test fun guestWorkoutNeverBecomesRetroactiveAfterLogin(): Unit=runBlocking {
        val id=start(); owner="account-A"; time+=60_000; finish(id)
        assertTrue(db.rankingDao().pending("account-A").isEmpty()); assertNotNull(db.dao().workout(id)?.endedAt)
    }
    @Test fun accountSwitchOrLogoutDoesNotCreditDifferentOwner(): Unit=runBlocking {
        owner="account-A"; val id=start(); owner="account-B"; time+=60_000; finish(id)
        assertTrue(db.rankingDao().pending("account-A").isEmpty()); assertTrue(db.rankingDao().pending("account-B").isEmpty())
        val next=start(); owner=null; time+=60_000; finish(next); assertTrue(db.rankingDao().pending("account-B").isEmpty())
    }
    @Test fun finishAndQueueAreAtomicIdempotentAndContainNoWorkoutDetails(): Unit=runBlocking {
        owner="account-A"; val id=start(); time+=60_000; finish(id); repo.finish(id)
        val event=db.rankingDao().pending(owner!!).single()
        assertEquals(db.dao().workout(id)!!.rankingEventId,event.eventId); assertEquals(0,event.awardedXp); assertEquals("pending",event.status)
        assertEquals(60_000L,event.completedAt-event.startedAt)
        assertEquals(7,RankingEvent::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) })
    }
    @Test fun shortWorkoutIsSavedLocallyWithoutXpEvent(): Unit=runBlocking {
        owner="account-A"; val id=start(); time+=59_999; finish(id)
        assertTrue(db.rankingDao().pending(owner!!).isEmpty()); assertNotNull(db.dao().workout(id)?.endedAt)
    }
    @Test fun selectiveHistoryDeletionKeepsQueueIdentifier(): Unit=runBlocking {
        owner="account-A"; val id=start(); time+=60_000; finish(id); val event=db.rankingDao().pending(owner!!).single()
        repo.deleteWorkoutHistory(id); assertEquals(event,db.rankingDao().pending(owner!!).single()); assertEquals(1,db.dao().exercises().size)
    }
    @Test fun restoredBackupCannotReawardOldWorkout(): Unit=runBlocking {
        owner="account-A"; val id=start(); val backup=BackupManager(ApplicationProvider.getApplicationContext(),db).snapshot()
        BackupManager(ApplicationProvider.getApplicationContext(),db).restoreJson(backup)
        assertNull(db.dao().workout(id)!!.rankingOwnerId); time+=60_000; finish(id); assertTrue(db.rankingDao().pending(owner!!).isEmpty())
    }
    @Test fun queueSurvivesReopeningDiskDatabase(): Unit=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>(); val name="ranking-persistence.db"; context.deleteDatabase(name)
        val disk=RitmoDatabase.open(context,name)
        val event=RankingEvent("event-one","account-A",1_000,61_000)
        disk.rankingDao().insert(event); disk.close()
        val reopened=RitmoDatabase.open(context,name)
        assertEquals(listOf(event),reopened.rankingDao().pending("account-A")); assertTrue(reopened.rankingDao().pending("account-B").isEmpty())
        reopened.close(); context.deleteDatabase(name)
    }
}
