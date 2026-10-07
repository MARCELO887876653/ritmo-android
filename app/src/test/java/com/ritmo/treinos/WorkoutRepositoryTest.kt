package com.ritmo.treinos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class WorkoutRepositoryTest {
    private lateinit var db: RitmoDatabase
    private lateinit var repo: WorkoutRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, RitmoDatabase::class.java).allowMainThreadQueries().build(); repo = WorkoutRepository(db) }
    @After fun close() { db.close() }
    private suspend fun complete(id: Long, weight: Double = 30.0): Long {
        val session = db.dao().exerciseSessions().first { it.workoutSessionId == id }
        val set = db.dao().sessionSets(session.id).first(); repo.saveSet(set.id, weight, 10, true); repo.finish(id); return session.id
    }
    @Test fun oneExerciseManySessionsAndSets(): Unit = runBlocking {
        val e = repo.saveExercise(null, "Supino reto", "")
        assertEquals(e, repo.saveExercise(null, "  SUPINO   RETO ", ""))
        val t = repo.saveTemplate(null, "Treino A", listOf(e))
        val first = repo.start(t); val firstEs = complete(first)
        val second = repo.start(t); val secondEs = db.dao().exerciseSessions().first { it.workoutSessionId == second }
        repo.copyPrevious(secondEs.id)
        assertEquals(30.0, db.dao().sessionSets(secondEs.id).single().weight, 0.0)
        assertFalse(db.dao().sessionSets(secondEs.id).single().completed)
        repo.addSet(secondEs.id); complete(second, 32.0)
        assertEquals(1, db.dao().exercises().size); assertEquals(2, db.dao().workouts().size)
        assertEquals(2, db.dao().exerciseSessions().size); assertEquals(3, db.dao().sets().size)
        assertEquals(firstEs, db.dao().previous(e, second)!!.session.id)
    }
    @Test fun startingTwiceResumesSingleSession(): Unit = runBlocking { val e = repo.saveExercise(null, "Agachamento", ""); val t = repo.saveTemplate(null, "Pernas", listOf(e)); assertEquals(repo.start(t), repo.start(t)); assertEquals(1, db.dao().workouts().size) }
    @Test fun removingSetRenumbersAndSnapshotsPreserveHistory(): Unit = runBlocking {
        val e = repo.saveExercise(null, "Supino", ""); val t = repo.saveTemplate(null, "Treino A", listOf(e)); val w = repo.start(t)
        val es = db.dao().exerciseSessions().single(); repo.addSet(es.id); repo.addSet(es.id)
        repo.removeSet(db.dao().sessionSets(es.id)[1].id)
        assertEquals(listOf(1, 2), db.dao().sessionSets(es.id).map { it.number })
        complete(w); repo.saveExercise(e, "Supino reto", ""); repo.deleteTemplate(t)
        assertEquals("Supino", db.dao().exerciseSessions().single().name); assertEquals("Treino A", db.dao().workouts().single().name)
        assertNull(db.dao().workouts().single().templateId)
    }
    @Test fun invalidInputDoesNotFinishOrOverwrite(): Unit = runBlocking {
        val e = repo.saveExercise(null, "Remada", ""); val t = repo.saveTemplate(null, "Costas", listOf(e)); val w = repo.start(t); val set = db.dao().sets().single()
        assertTrue(runCatching { repo.saveSet(set.id, -1.0, 10, true) }.isFailure)
        assertTrue(runCatching { repo.finish(w) }.isFailure); assertNull(db.dao().workout(w)!!.endedAt)
        complete(w); assertTrue(runCatching { repo.saveSet(set.id, 99.0, 10, true) }.isFailure)
    }
    @Test fun backupRoundTripAndInvalidRestoreAreAtomic(): Unit = runBlocking {
        val e = repo.saveExercise(null, "Supino", "nota"); val t = repo.saveTemplate(null, "A", listOf(e)); complete(repo.start(t))
        val manager = BackupManager(context, db); val before = manager.snapshot(); manager.restoreJson(before)
        assertEquals(before, manager.snapshot())
        val invalid = before.replace("\"exerciseId\": $e", "\"exerciseId\": 9999")
        assertTrue(runCatching { manager.restoreJson(invalid) }.isFailure); assertEquals(before, manager.snapshot())
    }
    @Test fun diskPersistenceSurvivesDatabaseReopen(): Unit = runBlocking {
        val name = "persistence-test.db"; context.deleteDatabase(name)
        val disk = RitmoDatabase.open(context, name); val repository = WorkoutRepository(disk)
        val e = repository.saveExercise(null, "Supino", ""); val t = repository.saveTemplate(null, "A", listOf(e)); val w = repository.start(t)
        val set = disk.dao().sets().single(); repository.saveSet(set.id, 32.0, 9, true); disk.close()
        val reopened = RitmoDatabase.open(context, name)
        assertEquals(w, reopened.dao().active()!!.id); assertEquals(32.0, reopened.dao().sets().single().weight, 0.0); assertTrue(reopened.dao().sets().single().completed)
        reopened.close(); context.deleteDatabase(name)
    }
}
