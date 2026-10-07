package com.ritmo.treinos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ritmo.treinos.data.AppSettings
import com.ritmo.treinos.ui.*
import com.ritmo.treinos.update.UpdateInfo
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vm: RitmoViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<RitmoApplication>()
        app.settings.prefs.edit().remove("updateJson").commit()
        app.settings.save(AppSettings(checkOnOpen = false))
        kotlinx.coroutines.runBlocking { val dao = app.database.dao(); dao.clearWorkouts(); dao.clearTemplates(); dao.clearExercises() }
        vm = RitmoViewModel(app); store.put("test", vm)
    }
    @After fun cleanup() { store.clear() }
    private fun info(force: Boolean, code: Int = BuildConfig.VERSION_CODE + 1) = UpdateInfo(code, "1.1.0", 1, force, "Teste de atualização", "https://github.com/${BuildConfig.GITHUB_REPOSITORY}/releases/download/v1.1.0/test.apk")
    @Test fun optionalUpdateHasLaterAndAllowsUse() {
        vm.update.value = info(false); compose.setContent { RitmoApp(vm) }
        compose.onNodeWithText("Nova atualização disponível").assertIsDisplayed()
        compose.onNodeWithText("Depois").performClick()
        compose.onNodeWithText("Nova atualização disponível").assertDoesNotExist()
        compose.onNodeWithText("Criar meu primeiro treino").assertExists()
    }
    @Test fun mandatoryUpdateDoesNotOfferDismissal() {
        vm.update.value = info(true); compose.setContent { RitmoApp(vm) }
        compose.onNodeWithText("Você precisa atualizar o aplicativo para continuar.").assertIsDisplayed()
        compose.onNodeWithText("Depois").assertDoesNotExist()
        compose.onNodeWithText("Atualizar", substring = false).assertIsDisplayed()
    }
    @Test fun alreadyInstalledVersionDoesNotShowDialog() {
        vm.update.value = info(true, BuildConfig.VERSION_CODE); compose.setContent { RitmoApp(vm) }
        compose.onNodeWithText("Atualização necessária").assertDoesNotExist()
        compose.onNodeWithText("Nova atualização disponível").assertDoesNotExist()
    }
}
