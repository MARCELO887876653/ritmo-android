package com.ritmo.treinos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.data.*
import com.ritmo.treinos.ui.date
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @LooperMode(LooperMode.Mode.PAUSED)
class HistoryDeletionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RitmoApplication>()
    @Before fun prepare() {
        runBlocking {
            app.settings.save(AppSettings(checkOnOpen = false, autoRest = false))
            app.settings.prefs.edit().remove("updateJson").commit()
            app.database.dao().apply { clearWorkouts(); clearTemplates(); clearExercises() }
        }
        waitText("RITMO")
    }
    private fun waitText(text: String) { compose.waitUntil(30000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun clickDescription(description: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performScrollTo().performClick()
    }
    private fun waitDeleted(workout: Long? = null, session: Long? = null) {
        compose.waitUntil(30000) { runBlocking { (workout == null || app.database.dao().workout(workout) == null) && (session == null || app.database.dao().exerciseSession(session) == null) } }
    }
    private data class Fixture(val exercise: Long, val older: Long, val newer: Long, val active: Long, val newerSession: Long, val oldTime: Long, val newTime: Long)
    private fun seed(): Fixture = runBlocking {
        val repo = app.repository; val dao = app.database.dao()
        val exercise = repo.saveExercise(null, "Supino", "")
        val sibling = repo.saveExercise(null, "Remada", "")
        val a = repo.saveTemplate(null, "Treino A", listOf(exercise, sibling))
        val b = repo.saveTemplate(null, "Treino B", listOf(exercise, sibling))
        suspend fun completed(template: Long, time: Long, weight: Double): Pair<Long, Long> {
            val workout = repo.start(template)
            val session = dao.exerciseSessions().first { it.workoutSessionId == workout && it.exerciseId == exercise }
            repo.saveSet(dao.sessionSets(session.id).single().id, weight, 10, true); repo.finish(workout)
            dao.updateWorkout(dao.workout(workout)!!.copy(startedAt = time, endedAt = time + 1800000))
            return workout to session.id
        }
        val now = System.currentTimeMillis(); val oldTime = now - 3 * 86400000L; val newTime = now - 86400000L
        val older = completed(a, oldTime, 30.0); val newer = completed(b, newTime, 35.0)
        Fixture(exercise, older.first, newer.first, repo.start(a), newer.second, oldTime, newTime)
    }
    private fun openHistory() {
        compose.onNodeWithText("Histórico", useUnmergedTree = true).performClick()
        waitText("Cada treino conta uma parte da sua jornada."); waitText("Treino B")
    }
    @Test fun deleteOneWorkoutFromHistoryCanBeCancelledAndPreservesOtherRecords() {
        val fixture = seed(); openHistory()
        val description = "Excluir treino Treino A de ${date(fixture.oldTime)} do histórico"
        clickDescription(description)
        compose.onNodeWithText("Excluir registro do treino?").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        runBlocking { assertNotNull(app.database.dao().workout(fixture.older)) }
        clickDescription(description)
        compose.onNodeWithText("Excluir registro").performClick(); waitDeleted(workout = fixture.older)
        compose.waitUntil(30000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() }
        runBlocking {
            assertNotNull(app.database.dao().workout(fixture.newer))
            assertEquals(fixture.active, app.database.dao().active()!!.id)
            assertEquals(2, app.database.dao().exercises().size)
            assertEquals(2, app.database.dao().templates().size)
        }
    }
    @Test fun deleteOneDateInExerciseHistoryKeepsSiblingExerciseAndOtherDates() {
        val fixture = seed()
        compose.onAllNodesWithText("Exercícios").onLast().performClick(); waitText("2 sessões registradas")
        compose.onNodeWithText("Supino").performClick(); waitText("Histórico por exercício")
        val description = "Excluir sessão de Supino de ${date(fixture.newTime)}"
        clickDescription(description)
        compose.onNodeWithText("Excluir sessão do exercício?").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        runBlocking { assertNotNull(app.database.dao().exerciseSession(fixture.newerSession)) }
        clickDescription(description)
        compose.onNodeWithText("Excluir sessão").performClick(); waitDeleted(session = fixture.newerSession)
        compose.waitUntil(30000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Último registro: 30 kg"))
        compose.onNodeWithText("Último registro: 30 kg").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Voltar").performClick(); waitText("1 sessões registradas")
        runBlocking {
            assertEquals(3, app.database.dao().workouts().size)
            assertEquals(1, app.database.dao().exerciseSessions().count { it.workoutSessionId == fixture.newer })
            assertEquals(fixture.active, app.database.dao().active()!!.id)
            assertFalse(app.database.dao().exercise(fixture.exercise)!!.archived)
        }
    }
    @Test fun deletingWorkoutFromDetailReturnsToHistory() {
        val fixture = seed(); openHistory()
        compose.onNodeWithText("Treino B").performClick(); waitText("Término:")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Excluir treino do histórico"))
        compose.onNodeWithText("Excluir treino do histórico").performScrollTo().performClick()
        compose.onNodeWithText("Excluir registro").performClick(); waitDeleted(workout = fixture.newer)
        waitText("Cada treino conta uma parte da sua jornada.")
        runBlocking { assertNotNull(app.database.dao().workout(fixture.older)); assertEquals(fixture.active, app.database.dao().active()!!.id) }
    }
}
