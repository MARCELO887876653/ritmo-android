package com.ritmo.treinos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ritmo.treinos.ui.*
import com.ritmo.treinos.update.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35]) @LooperMode(LooperMode.Mode.PAUSED)
class DownloadUiTest {
    @get:Rule val compose = createComposeRule()
    private val info = UpdateInfo(BuildConfig.VERSION_CODE + 1, "1.1.0", 1, false, "Melhorias", "https://github.com/${BuildConfig.GITHUB_REPOSITORY}/releases/download/v1.1.0/ritmo.apk")
    @Test fun downloadShowsProgressAllowsCancelAndThenOffersInstaller() {
        var state by mutableStateOf(ApkDownloadState())
        var installed = false; var later = false
        compose.setContent { RitmoTheme("dark") {
            UpdateDialog(info, false, state, null, null,
                { state = ApkDownloadState(DownloadPhase.DOWNLOADING, info, 50, 100) },
                { state = ApkDownloadState() }, { installed = true }, { later = true }, {})
        } }
        compose.onNodeWithText("Atualizar agora").performClick()
        compose.onNodeWithText("50%", substring = true).assertExists()
        compose.onNodeWithText("Baixando…").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar download").performScrollTo().performClick()
        compose.onNodeWithText("Atualizar agora").assertExists()
        assertFalse(installed)
        compose.runOnIdle { state = ApkDownloadState(DownloadPhase.READY, info, 100, 100) }
        compose.onNodeWithText("Download concluído").assertExists()
        compose.onNodeWithText("Instalar atualização").performClick(); assertTrue(installed)
        compose.onNodeWithText("Depois").performClick(); assertTrue(later)
    }
    @Test fun mandatoryDownloadRemainsBlockingAndFailedDownloadOffersRetry() {
        var state by mutableStateOf(ApkDownloadState(DownloadPhase.PAUSED, info, 50, 100))
        var retry = false
        compose.setContent { RitmoTheme("dark") {
            UpdateDialog(info.copy(forceUpdate = true), true, state, null, null,
                { retry = true }, { state = ApkDownloadState() }, {}, {}, {})
        } }
        compose.onNodeWithText("Download pausado", substring = true).assertExists()
        compose.onNodeWithText("Depois").assertDoesNotExist()
        compose.runOnIdle { state = ApkDownloadState(DownloadPhase.FAILED, info, error = "Sem espaço") }
        compose.onNodeWithText("Sem espaço").assertExists()
        compose.onNodeWithText("Tentar novamente").performClick(); assertTrue(retry)
        compose.onNodeWithText("Depois").assertDoesNotExist()
    }
}
