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
class HistoryDeletionTest {
    private lateinit var db: RitmoDatabase
    private lateinit var repo: WorkoutRepository
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(context, RitmoDatabase::class.java).allowMainThreadQueries().build(); repo = WorkoutRepository(db) }
    @After fun close() { db.close() }
    private suspend fun finish(workout: Long, weight: Double = 30.0): Long {
        val session = db.dao().exerciseSessions().first { it.workoutSessionId == workout }
        val set = db.dao().sessionSets(session.id).first()
        repo.saveSet(set.id, weight, 10, true); repo.finish(workout); return session.id
    }
    @Test fun deletingOneWorkoutCascadesOnlyItsRowsAndKeepsCatalogTemplatesAndActiveWorkout(): Unit = runBlocking {
        val first = repo.saveExercise(null, "Supino", "nota")
        val second = repo.saveExercise(null, "Remada", "")
        val template = repo.saveTemplate(null, "Treino A", listOf(first, second))
        val removed = repo.start(template); finish(removed)
        val retained = repo.start(template); finish(retained, 35.0)
        val active = repo.start(template)
        val dao = db.dao(); val exercises = dao.exercises(); val templates = dao.templates(); val links = dao.links()
        val workouts = dao.workouts(); val sessions = dao.exerciseSessions(); val sets = dao.sets()
        val removedSessions = sessions.filter { it.workoutSessionId == removed }.map { it.id }.toSet()
        repo.deleteWorkoutHistory(removed); repo.deleteWorkoutHistory(removed)
        assertEquals(workouts.filter { it.id != removed }, dao.workouts())
        assertEquals(sessions.filter { it.id !in removedSessions }, dao.exerciseSessions())
        assertEquals(sets.filter { it.exerciseSessionId !in removedSessions }, dao.sets())
        assertEquals(exercises, dao.exercises()); assertEquals(templates, dao.templates()); assertEquals(links, dao.links())
        assertEquals(active, dao.active()!!.id); assertNotNull(dao.workout(retained))
    }
    @Test fun deletingOneExerciseSessionKeepsSiblingsAndOtherDatesAndUpdatesPreviousCopy(): Unit = runBlocking {
        val first = repo.saveExercise(null, "Supino", "")
        val second = repo.saveExercise(null, "Remada", "")
        val template = repo.saveTemplate(null, "A", listOf(first, second))
        val older = repo.start(template); val olderSession = finish(older, 30.0)
        val newer = repo.start(template); val removed = finish(newer, 45.0)
        val active = repo.start(template)
        val dao = db.dao(); val workouts = dao.workouts(); val sessions = dao.exerciseSessions(); val sets = dao.sets()
        assertEquals(removed, dao.previous(first, active)!!.session.id)
        repo.deleteExerciseHistory(removed)
        assertEquals(workouts, dao.workouts())
        assertEquals(sessions.filter { it.id != removed }, dao.exerciseSessions())
        assertEquals(sets.filter { it.exerciseSessionId != removed }, dao.sets())
        assertEquals(olderSession, dao.previous(first, active)!!.session.id)
        val current = dao.exerciseSessions().first { it.workoutSessionId == active && it.exerciseId == first }
        repo.copyPrevious(current.id)
        assertEquals(30.0, dao.sessionSets(current.id).single().weight, 0.0)
        assertFalse(dao.sessionSets(current.id).single().completed)
        assertEquals(listOf(first, second), dao.templateLinks(template).map { it.exerciseId })
        assertEquals(2, dao.exercises().size)
    }
    @Test fun historyDeletionCannotRemoveActiveWorkoutOrItsExerciseSession(): Unit = runBlocking {
        val exercise = repo.saveExercise(null, "Agachamento", "")
        val template = repo.saveTemplate(null, "Pernas", listOf(exercise))
        val active = repo.start(template); val session = db.dao().exerciseSessions().single().id
        val manager = BackupManager(context, db); val before = manager.snapshot()
        assertTrue(runCatching { repo.deleteWorkoutHistory(active) }.isFailure)
        assertTrue(runCatching { repo.deleteExerciseHistory(session) }.isFailure)
        assertEquals(0, db.dao().deleteFinishedWorkout(active))
        assertEquals(0, db.dao().deleteFinishedExerciseSession(session))
        assertEquals(before, manager.snapshot())
    }
    @Test fun removingLastHistoricalExerciseKeepsWorkoutAndSurvivesReopenAndBackupRestore(): Unit = runBlocking {
        val name = "history-delete-persistence.db"; context.deleteDatabase(name)
        val disk = RitmoDatabase.open(context, name); val repository = WorkoutRepository(disk)
        val exercise = repository.saveExercise(null, "Supino", "")
        val template = repository.saveTemplate(null, "A", listOf(exercise))
        val finished = repository.start(template); val session = disk.dao().exerciseSessions().single()
        repository.saveSet(disk.dao().sets().single().id, 32.0, 9, true); repository.finish(finished)
        val active = repository.start(template)
        repository.deleteExerciseHistory(session.id); disk.close()
        val reopened = RitmoDatabase.open(context, name)
        try {
            assertNotNull(reopened.dao().workout(finished)!!.endedAt)
            assertEquals(active, reopened.dao().active()!!.id)
            assertEquals(active, reopened.dao().exerciseSessions().single().workoutSessionId)
            assertEquals(1, reopened.dao().sets().size)
            assertEquals(exercise, reopened.dao().exercises().single().id)
            val manager = BackupManager(context, reopened); val backup = manager.snapshot()
            manager.restoreJson(backup); assertEquals(backup, manager.snapshot())
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
