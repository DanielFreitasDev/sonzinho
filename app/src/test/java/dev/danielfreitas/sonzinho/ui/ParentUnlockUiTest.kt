package dev.danielfreitas.sonzinho.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielfreitas.sonzinho.testing.FakeAudioEngine
import dev.danielfreitas.sonzinho.testing.FakeLibraryRepository
import dev.danielfreitas.sonzinho.testing.FakeSettingsRepository
import dev.danielfreitas.sonzinho.ui.parent.ParentChallengeOverlay
import dev.danielfreitas.sonzinho.ui.parent.UnlockCornerDetector
import dev.danielfreitas.sonzinho.ui.theme.SonzinhoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fluxo completo de desbloqueio: gesto no canto + desafio, com o ViewModel real e repositórios falsos. */
@RunWith(AndroidJUnit4::class)
class ParentUnlockUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        vm = MainViewModel(FakeSettingsRepository(), FakeLibraryRepository(), FakeAudioEngine())
        compose.setContent {
            SonzinhoTheme {
                val gate by vm.gate.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().testTag("superficie")) {
                    UnlockCornerDetector(onHeld = vm::onCornerHeld) { Box(Modifier.fillMaxSize()) }
                    ParentChallengeOverlay(gate, vm::submitAnswer, vm::dismissGate)
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    /** Dedo parado no canto: micro-movimentos como os de um dedo real, com o relógio avançando junto. */
    private fun holdCorner(
        millis: Long,
        extraFinger: Boolean = false,
    ) {
        compose.onNodeWithTag("superficie").performTouchInput {
            down(0, Offset(20f, 20f))
            if (extraFinger) down(1, Offset(centerX, centerY))
            var elapsed = 0L
            while (elapsed < millis) {
                advanceEventTime(50)
                elapsed += 50
                updatePointerBy(0, Offset(if (elapsed % 100 == 0L) 0.5f else -0.5f, 0f))
                if (extraFinger) updatePointerBy(1, Offset(0.5f, 0f))
                move()
            }
            if (extraFinger) up(1)
            up(0)
        }
        compose.mainClock.advanceTimeBy(100)
    }

    private fun typeAnswer(answer: String) {
        answer.forEach { d -> compose.onNodeWithTag("tecla_$d").performClick() }
        compose.onNodeWithTag("confirmar").performClick()
        compose.mainClock.advanceTimeBy(100)
    }

    @Test
    fun segurar_o_canto_tres_segundos_e_acertar_a_conta_abre_a_area_dos_pais() {
        holdCorner(3200)
        assertTrue("estado: ${vm.gate.value}", vm.gate.value is GateState.Math)
        compose.onNodeWithText("Área dos pais").assertExists()
        val gate = vm.gate.value as GateState.Math
        typeAnswer(gate.challenge.answer.toString())
        assertEquals(Screen.ParentHome, vm.screen.value)
        compose.onNodeWithTag("desafio_pais").assertDoesNotExist()
    }

    @Test
    fun soltar_antes_de_tres_segundos_nao_abre_nada() {
        holdCorner(1500)
        assertEquals(GateState.Hidden, vm.gate.value)
        compose.onNodeWithTag("desafio_pais").assertDoesNotExist()
    }

    @Test
    fun palma_com_varios_dedos_nao_abre_o_desafio() {
        holdCorner(4000, extraFinger = true)
        assertEquals(GateState.Hidden, vm.gate.value)
    }

    @Test
    fun conta_errada_mantem_o_bebe_brincando() {
        holdCorner(3200)
        assertTrue("estado: ${vm.gate.value}", vm.gate.value is GateState.Math)
        typeAnswer("1")
        assertEquals(Screen.Baby, vm.screen.value)
        compose.onNodeWithText("Ops, não foi dessa vez. Tente de novo.").assertExists()
        compose.onNodeWithText("Voltar a brincar").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(GateState.Hidden, vm.gate.value)
    }
}
