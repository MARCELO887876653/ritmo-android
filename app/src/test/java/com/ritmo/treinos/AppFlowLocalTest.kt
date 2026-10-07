package com.ritmo.treinos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import com.ritmo.treinos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppFlowLocalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RitmoApplication>()
    @Before fun prepare() {
        runBlocking {
        app.settings.save(AppSettings(checkOnOpen = false, autoRest = false))
        app.settings.prefs.edit().remove("updateJson").commit()
        val dao = app.database.dao(); dao.clearWorkouts(); dao.clearTemplates(); dao.clearExercises()
        }
        waitText("RITMO")
    }
    private fun waitText(text: String) { compose.waitUntil(30000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun clickScrolled(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo()
        clickEnabled(text)
    }
    private fun clickEnabled(text: String) {
        compose.waitUntil(10000) {
            !compose.onNodeWithText(text).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }
        compose.onNodeWithText(text).performClick()
    }
    @Test fun createExerciseTemplateAndRecordTwoSessions() {
        compose.onAllNodesWithText("Exercícios").onLast().performClick()
        compose.onNodeWithText("Novo exercício").performClick()
        compose.onNodeWithText("Nome do exercício").performTextInput("Supino reto")
        compose.onNodeWithText("Salvar exercício").performClick(); waitText("Histórico por exercício")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Treinos", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Criar treino").performClick()
        compose.onNodeWithText("Nome do treino").performTextInput("Treino A")
        clickScrolled("Supino reto")
        clickScrolled("Salvar treino"); waitText("Meus treinos")
        compose.onNodeWithText("Começar").performClick(); waitText("Seu progresso é salvo automaticamente."); waitText("Treino A")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Carga (kg)"))
        compose.onNodeWithText("Carga (kg)").performTextInput("30")
        compose.onNodeWithText("Repetições").performTextInput("10")
        compose.onNode(isToggleable()).performScrollTo().performClick(); waitText("Série concluída")
        clickScrolled("Finalizar treino")
        clickEnabled("Finalizar"); waitText("Término:"); compose.waitForIdle()
        compose.onNodeWithContentDescription("Voltar").performClick(); waitText("RITMO")
        clickScrolled("Começar treino"); waitText("Copiar séries anteriores")
        compose.onNodeWithText("Copiar séries anteriores").performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("30").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isToggleable()).performScrollTo().performClick(); waitText("Série concluída")
        clickScrolled("Finalizar treino")
        clickEnabled("Finalizar"); waitText("Término:"); compose.waitForIdle()
        runBlocking { Assert.assertEquals(1, app.database.dao().exercises().size); Assert.assertEquals(2, app.database.dao().workouts().size); Assert.assertEquals(2, app.database.dao().exerciseSessions().size) }
    }
    @Test fun reopensActiveSessionAndNavigatesHistoryOffline() {
        runBlocking {
            val e = app.repository.saveExercise(null, "Remada", "")
            val t = app.repository.saveTemplate(null, "Costas", listOf(e)); app.repository.start(t)
            val set = app.database.dao().sets().single(); app.repository.saveSet(set.id, 25.0, 12, true)
        }
        compose.activityRule.scenario.recreate(); waitText("Continuar treino")
        clickScrolled("Continuar treino"); waitText("Seu progresso é salvo automaticamente."); waitText("Série concluída")
        compose.onNodeWithText("25").assertExists()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Histórico", useUnmergedTree = true).performClick(); waitText("Ainda sem registros")
        compose.onNodeWithText("Progresso", useUnmergedTree = true).performClick(); waitText("Um passo de cada vez")
    }
    @Test fun deleteExerciseFromCatalogRequiresConfirmationAndKeepsHistory() {
        val exercise = runBlocking {
            val id = app.repository.saveExercise(null, "Supino reto", "")
            val template = app.repository.saveTemplate(null, "Treino A", listOf(id))
            val workout = app.repository.start(template)
            app.repository.saveSet(app.database.dao().sets().single().id, 30.0, 10, true)
            app.repository.finish(workout)
            id
        }
        compose.onAllNodesWithText("Exercícios").onLast().performClick(); waitText("1 sessões registradas")
        compose.onNodeWithContentDescription("Excluir exercício Supino reto").performClick()
        compose.onNodeWithText("Excluir exercício?").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithContentDescription("Excluir exercício Supino reto").assertExists().performClick()
        compose.onNodeWithText("Excluir").performClick(); waitText("Nenhum exercício aqui")
        runBlocking {
            Assert.assertTrue(app.database.dao().exercise(exercise)!!.archived)
            Assert.assertTrue(app.database.dao().links().isEmpty())
            Assert.assertEquals(exercise, app.database.dao().exerciseSessions().single().exerciseId)
            Assert.assertEquals(30.0, app.database.dao().sets().single().weight, 0.0)
        }
    }
}
