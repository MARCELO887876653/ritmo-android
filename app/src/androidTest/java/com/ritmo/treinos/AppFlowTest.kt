package com.ritmo.treinos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritmo.treinos.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<RitmoApplication>()
    @Before fun prepare() = runBlocking {
        app.settings.save(AppSettings(checkOnOpen = false, autoRest = false))
        app.settings.prefs.edit().remove("updateJson").commit()
        val dao = app.database.dao(); dao.clearWorkouts(); dao.clearTemplates(); dao.clearExercises()
    }
    private fun waitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun createExerciseTemplateAndRecordTwoSessions() {
        compose.onAllNodesWithText("Exercícios").onLast().performClick()
        compose.onNodeWithText("Novo exercício").performClick()
        compose.onNodeWithText("Nome do exercício").performTextInput("Supino reto")
        compose.onNodeWithText("Salvar exercício").performClick(); waitText("Histórico por exercício")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Treinos", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Criar treino").performClick()
        compose.onNodeWithText("Nome do treino").performTextInput("Treino A")
        compose.onNodeWithText("Supino reto").performScrollTo().performClick()
        compose.onNodeWithText("Salvar treino").performScrollTo().performClick(); waitText("Meus treinos")
        compose.onNodeWithText("Começar").performClick(); waitText("Seu progresso é salvo automaticamente.")
        compose.onNodeWithText("Carga (kg)").performTextInput("30")
        compose.onNodeWithText("Repetições").performTextInput("10")
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Finalizar treino").performScrollTo().performClick()
        compose.onNodeWithText("Finalizar", substring = false).performClick(); waitText("1 séries concluídas")
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Começar treino").performClick(); waitText("Copiar séries anteriores")
        compose.onNodeWithText("Copiar séries anteriores").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("30").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Finalizar treino").performScrollTo().performClick()
        compose.onNodeWithText("Finalizar", substring = false).performClick(); waitText("1 séries concluídas")
        runBlocking { Assert.assertEquals(1, app.database.dao().exercises().size); Assert.assertEquals(2, app.database.dao().workouts().size); Assert.assertEquals(2, app.database.dao().exerciseSessions().size) }
    }
    @Test fun reopensActiveSessionAndNavigatesHistoryOffline() {
        runBlocking {
            val e = app.repository.saveExercise(null, "Remada", "")
            val t = app.repository.saveTemplate(null, "Costas", listOf(e)); app.repository.start(t)
            val set = app.database.dao().sets().single(); app.repository.saveSet(set.id, 25.0, 12, true)
        }
        compose.activityRule.scenario.recreate(); waitText("Continuar treino")
        compose.onNodeWithText("Continuar treino").performClick(); waitText("Série concluída")
        compose.onNodeWithText("25").assertExists()
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Histórico", useUnmergedTree = true).performClick(); waitText("Ainda sem registros")
        compose.onNodeWithText("Progresso", useUnmergedTree = true).performClick(); waitText("Um passo de cada vez")
    }
}
