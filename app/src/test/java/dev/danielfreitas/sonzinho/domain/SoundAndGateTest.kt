package dev.danielfreitas.sonzinho.domain

import dev.danielfreitas.sonzinho.domain.audio.SynthPatch
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.SoundShape
import dev.danielfreitas.sonzinho.domain.music.SoundDesigner
import dev.danielfreitas.sonzinho.domain.usecase.FreePlayRecorder
import dev.danielfreitas.sonzinho.domain.usecase.MathChallenge
import dev.danielfreitas.sonzinho.domain.usecase.PinHasher
import dev.danielfreitas.sonzinho.domain.usecase.UnlockGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SoundDesignerTest {
    @Test
    fun `patch tem exatamente o formato esperado pelo motor nativo`() {
        assertEquals(SynthPatch.PARAM_COUNT, SynthPatch().toFloatArray().size)
    }

    @Test
    fun `todos os controles extremos geram sons seguros`() {
        val values = listOf(0f, 0.5f, 1f, -3f, 7f)
        for (shape in SoundShape.entries) {
            for (v in values) {
                val patch = SoundDesigner.toPatch(SoundParams(shape, v, v, v, v, v))
                val arr = patch.toFloatArray()
                assertTrue(arr.all { it.isFinite() })
                assertTrue("filtro nunca estridente", patch.cutoffHz in 250f..6000f)
                assertTrue("ataque sem clique", patch.attack >= 0.004f)
                assertTrue("ganho contido", patch.gain <= 0.9f)
                assertTrue(patch.echoSend <= 0.55f)
                assertTrue("toda nota termina sozinha", patch.maxSeconds in 0.3f..3f)
            }
        }
    }

    @Test
    fun `mais brilhante abre o filtro e mais longo aumenta a liberacao`() {
        val soft = SoundDesigner.toPatch(SoundParams(brightness = 0f, length = 0f))
        val bright = SoundDesigner.toPatch(SoundParams(brightness = 1f, length = 1f))
        assertTrue(bright.cutoffHz > soft.cutoffHz * 5)
        assertTrue(bright.release > soft.release)
    }

    @Test
    fun `eixo grave agudo cobre duas oitavas para cada lado`() {
        assertEquals(36f, SoundDesigner.midiFor(SoundParams(pitch = 0f)), 1e-4f)
        assertEquals(84f, SoundDesigner.midiFor(SoundParams(pitch = 1f)), 1e-4f)
    }
}

class ParentalGateTest {
    @Test
    fun `continha sempre tem vai-um e resposta de dois digitos`() {
        val random = Random(1)
        repeat(500) {
            val c = MathChallenge.generate(random)
            assertTrue(c.answer in 11..18)
            assertTrue(c.isCorrect(" ${c.answer} "))
            assertFalse(c.isCorrect((c.answer + 1).toString()))
            assertFalse(c.isCorrect("abc"))
            assertTrue(c.question.startsWith("Quanto é"))
        }
    }

    @Test
    fun `pin e verificado pelo hash e nunca guardado em texto`() {
        val stored = PinHasher.hash("2468")
        assertFalse(stored.contains("2468"))
        assertTrue(PinHasher.verify("2468", stored))
        assertFalse(PinHasher.verify("2469", stored))
        assertFalse(PinHasher.verify("2468", "lixo"))
        assertNotEquals("sal aleatório", stored, PinHasher.hash("2468"))
    }

    @Test
    fun `formato do pin`() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test
    fun `gesto exige um unico dedo por tres segundos`() {
        assertTrue(UnlockGesture.isValid(1))
        assertFalse(UnlockGesture.isValid(0))
        assertFalse("palma do bebê", UnlockGesture.isValid(4))
        assertEquals(0.5f, UnlockGesture.progress(1500), 1e-4f)
        assertEquals(1f, UnlockGesture.progress(9000), 1e-4f)
    }
}

class FreePlayRecorderTest {
    @Test
    fun `grava tempos relativos ao inicio e so enquanto ativo`() {
        var now = 1000L
        val recorder = FreePlayRecorder { now }
        recorder.record(1, 60f, 0.8f, 0f) // ignorado: não está gravando
        recorder.start()
        now = 1250
        recorder.record(1, 62f, 0.8f, 0f)
        now = 2000
        recorder.record(2, 64f, 0.6f, 0.5f)
        now = 2600
        val (notes, duration) = recorder.stop()
        assertEquals(listOf(250L, 1000L), notes.map { it.timeMs })
        assertEquals(500L, notes[1].durationMs)
        assertEquals(1600L, duration)
        assertFalse(recorder.recording.value)
    }
}
