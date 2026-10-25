package dev.danielfreitas.sonzinho.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.domain.usecase.FreePlayRecorder
import dev.danielfreitas.sonzinho.testing.FakeAudioEngine
import dev.danielfreitas.sonzinho.testing.FakeLibraryRepository
import dev.danielfreitas.sonzinho.testing.FakeSettingsRepository
import dev.danielfreitas.sonzinho.ui.parent.ParentHomeScreen
import dev.danielfreitas.sonzinho.ui.parent.SettingsScreen
import dev.danielfreitas.sonzinho.ui.parent.SettingsViewModel
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val settings = FakeSettingsRepository()
    private val vm = SettingsViewModel(settings, FakeLibraryRepository(), FakeAudioEngine(), FreePlayRecorder { 0L })

    @Test
    fun configuracoes_em_portugues_e_criar_pin() {
        // O cursor piscando do campo de texto é uma animação infinita: relógio manual.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SonzinhoTheme {
                SettingsScreen(onBack = {}, onLockGuide = {}, onCredits = {}, isDeviceOwner = false, onRemoveDeviceOwner = {}, viewModel = vm)
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Volume máximo").assertExists()
        compose.onNodeWithText("Pentatônica alegre").assertExists()
        compose.onNodeWithTag("trocar_pin").performScrollTo().performClick()
        compose.onNodeWithText("PIN dos pais").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("novo_pin").performTextInput("2580")
        compose.onNodeWithTag("repita_pin").performTextInput("2580")
        compose.onNodeWithText("Salvar").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue(settings.settings.value.hasPin)
    }

    @Test
    fun area_dos_pais_escolhe_a_brincadeira_e_volta_ao_bebe() {
        var backToBaby = false
        compose.setContent {
            SonzinhoTheme {
                ParentHomeScreen(onNavigate = {}, onBackToBaby = { backToBaby = true }, onExitApp = {}, viewModel = vm)
            }
        }
        compose.onNodeWithTag("brincadeira_Teclado colorido").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(BabyActivity.KEYBOARD, settings.settings.value.babyActivity)
        compose.onNodeWithTag("voltar_bebe").performScrollTo().performClick()
        assertTrue(backToBaby)
    }
}
