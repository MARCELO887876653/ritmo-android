package com.ritmo.treinos
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.ui.*
import com.ritmo.treinos.data.AppSettings
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35]) @LooperMode(LooperMode.Mode.PAUSED)
class RankingUiTest {
    @get:Rule val compose=createComposeRule()
    private val store=ViewModelStore()
    private lateinit var model:RitmoViewModel
    private val app get()=ApplicationProvider.getApplicationContext<RitmoApplication>()
    private fun vm()=model
    @Before fun setup() {app.settings.save(AppSettings(checkOnOpen=false)); model=RitmoViewModel(app); store.put("online-ui",model)}
    @After fun cleanup() {store.clear()}
    @Test fun guestCanSwitchLoginAndSignupForms() {
        compose.setContent { RitmoTheme("dark") { AccountScreen(vm(),{}, {}) } }
        compose.onNodeWithText("Entrar com Google").assertExists()
        compose.onNodeWithText("E-mail").assertExists(); compose.onNodeWithText("Senha").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Criar uma conta"))
        compose.onNodeWithText("Criar uma conta").performClick(); compose.onNodeWithText("Criar conta").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Entrar com Google"))
        compose.onNodeWithText("Entrar com Google").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Já tenho conta"))
        compose.onNodeWithText("Já tenho conta").performClick(); compose.onNodeWithText("Entrar").assertExists()
    }
    @Test fun profileAndPrivacyAreAccessibleAsGuest() {
        var destination=""
        var privacy by mutableStateOf(false)
        compose.setContent { RitmoTheme("dark") { if(privacy) PrivacyScreen(vm(),{}, {}) else ProfileScreen(vm(),{}, {destination=it}) } }
        compose.onNodeWithText("Entrar ou criar conta").performClick(); Assert.assertEquals("account",destination)
        compose.runOnIdle { privacy=true }
        compose.onNodeWithText("Conta e privacidade").assertExists(); compose.onNodeWithText("Entrar ou criar conta").assertExists()
    }
    @Test fun achievementsDescribeDailyCapWithoutPhysicalTargets() {
        compose.setContent { RitmoTheme("dark") { AchievementsScreen(vm(),{}, {}) } }
        compose.onNodeWithText("XP e conquistas").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Primeiro treino válido",substring=true))
        compose.onNodeWithText("Limite: 125 XP",substring=true).assertExists()
    }
}
