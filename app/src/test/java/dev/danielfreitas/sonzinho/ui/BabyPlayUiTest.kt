package dev.danielfreitas.sonzinho.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.testing.PlayedNote
import dev.danielfreitas.sonzinho.ui.baby.AnimalsScreen
import dev.danielfreitas.sonzinho.ui.baby.KeyboardScreen
import dev.danielfreitas.sonzinho.ui.baby.MagicScreen
import dev.danielfreitas.sonzinho.ui.baby.rememberBabyHaptics
import dev.danielfreitas.sonzinho.ui.baby.rememberEffectsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Toque em qualquer lugar soa; multitoque com muitos dedos não trava nem perde notas. */
@RunWith(AndroidJUnit4::class)
class BabyPlayUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val played = mutableListOf<PlayedNote>()
    private val settings = AppSettings(instrumentId = Instrument.MARIMBA.id, keyboardKeys = 6)

    private fun show(screen: String) {
        // As animações do bebê nunca param: o relógio do Compose é controlado à mão.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val effects = rememberEffectsState(1f, 1f)
            val haptics = rememberBabyHaptics(false)
            val onPlay: (Int, Float, Float, Float) -> Unit = { id, midi, vel, _ -> played += PlayedNote(id, midi, vel) }
            Box(Modifier.fillMaxSize().testTag("tela")) {
                when (screen) {
                    "magica" -> MagicScreen(settings, effects, haptics, onPlay, Modifier.fillMaxSize())
                    "teclado" -> KeyboardScreen(settings, effects, haptics, onPlay, Modifier.fillMaxSize())
                    else -> AnimalsScreen(settings, emptyList(), effects, haptics, onPlay, Modifier.fillMaxSize())
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun settle() = compose.mainClock.advanceTimeBy(48)

    @Test
    fun tela_magica_toca_em_qualquer_ponto_com_grave_a_esquerda() {
        show("magica")
        compose.onNodeWithTag("tela").performTouchInput {
            down(Offset(10f, bottom - 10f))
            up()
            down(Offset(right - 10f, 10f))
            up()
        }
        settle()
        assertEquals(2, played.size)
        assertTrue("grave embaixo/esquerda < agudo em cima/direita", played[0].midi < played[1].midi)
        assertTrue(played.all { it.instrumentId == Instrument.MARIMBA.id })
    }

    @Test
    fun mao_inteira_com_dez_dedos_toca_dez_notas() {
        show("magica")
        compose.onNodeWithTag("tela").performTouchInput {
            for (i in 0 until 10) down(i, Offset(100f + i * 90f, 300f + (i % 3) * 40f))
            for (i in 0 until 10) up(i)
        }
        settle()
        assertEquals(10, played.size)
        assertTrue(played.all { it.velocity in 0.45f..0.92f })
    }

    @Test
    fun arrastar_o_dedo_faz_glissando() {
        show("magica")
        compose.onNodeWithTag("tela").performTouchInput {
            down(Offset(20f, centerY))
            for (k in 1..20) {
                advanceEventTime(80)
                moveTo(Offset(20f + k * (width - 40f) / 20f, centerY))
            }
            up()
        }
        settle()
        assertTrue("várias notas no arrasto: ${played.size}", played.size >= 5)
    }

    @Test
    fun teclado_sobe_da_esquerda_para_a_direita() {
        show("teclado")
        compose.onNodeWithTag("tela").performTouchInput {
            for (k in 0 until 6) {
                down(Offset(width * (k + 0.5f) / 6f, centerY))
                up()
            }
        }
        settle()
        assertEquals(6, played.size)
        assertEquals(played.map { it.midi }.sorted(), played.map { it.midi })
        assertEquals(6, played.map { it.midi }.toSet().size)
    }

    @Test
    fun bichinhos_cantam_com_voz_de_bichinho_em_qualquer_lugar() {
        show("bichinhos")
        compose.onNodeWithTag("tela").performTouchInput {
            listOf(Offset(5f, 5f), center, Offset(right - 5f, bottom - 5f)).forEach {
                down(it)
                up()
            }
        }
        settle()
        assertEquals(3, played.size)
        assertTrue(played.all { it.instrumentId in Instrument.BIRD.id..Instrument.WHALE.id })
    }
}
