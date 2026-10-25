package dev.danielfreitas.sonzinho.domain

import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.music.MusicTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTheoryTest {
    @Test
    fun `pentatonica maior de Do tem so as cinco notas`() {
        val notes = MusicTheory.scaleNotes(Scale.PENTATONIC_MAJOR, MusicKey.C, 60, 72)
        assertEquals(listOf(60, 62, 64, 67, 69, 72), notes)
    }

    @Test
    fun `tonalidade desloca a escala`() {
        val notes = MusicTheory.scaleNotes(Scale.PENTATONIC_MAJOR, MusicKey.D, 60, 72)
        assertEquals(listOf(62, 64, 66, 69, 71), notes)
    }

    @Test
    fun `graus alem da escala sobem e descem de oitava`() {
        val scale = Scale.PENTATONIC_MAJOR
        assertEquals(60, MusicTheory.noteForDegree(scale, MusicKey.C, 60, 0))
        assertEquals(72, MusicTheory.noteForDegree(scale, MusicKey.C, 60, 5))
        assertEquals(74, MusicTheory.noteForDegree(scale, MusicKey.C, 60, 6))
        assertEquals(57, MusicTheory.noteForDegree(scale, MusicKey.C, 60, -1))
    }

    @Test
    fun `notas tocaveis ficam na regiao bonita do instrumento`() {
        Instrument.melodic.forEach { inst ->
            val notes = MusicTheory.playableNotes(Scale.PENTATONIC_MAJOR, MusicKey.C, inst, 13)
            assertTrue(inst.name, notes.isNotEmpty() && notes.size <= 13)
            assertTrue(inst.name, notes.all { it in inst.lowestMidi..inst.highestMidi })
            // Nada acima de Dó7 (2093 Hz): agudos estridentes ficam de fora.
            assertTrue(inst.name, notes.all { it <= 96 })
        }
    }

    @Test
    fun `tela magica tem grave embaixo a esquerda e agudo em cima a direita`() {
        val regions = 13
        val low = MusicTheory.magicRegion(5f, 995f, 2000f, 1000f, regions)
        val high = MusicTheory.magicRegion(1995f, 5f, 2000f, 1000f, regions)
        val middle = MusicTheory.magicRegion(1000f, 500f, 2000f, 1000f, regions)
        assertEquals(0, low)
        assertEquals(regions - 1, high)
        assertTrue(middle in 1 until regions - 1)
    }

    @Test
    fun `qualquer ponto da tela cai em alguma regiao (sem tela vazia)`() {
        for (x in 0..20) {
            for (y in 0..10) {
                val r = MusicTheory.magicRegion(x * 100f, y * 100f, 2000f, 1000f, 13)
                assertTrue(r in 0 until 13)
            }
        }
        // Mesmo fora dos limites (toque na borda) não quebra.
        assertTrue(MusicTheory.magicRegion(-50f, 5000f, 2000f, 1000f, 13) in 0 until 13)
    }

    @Test
    fun `velocidade do toque e gentil e limitada`() {
        listOf(Float.NaN, 0f, 0.1f, 1f, 5f).forEach { p ->
            val v = MusicTheory.velocityForTouch(p, 1f)
            assertTrue(v in 0.45f..0.92f)
        }
    }

    @Test
    fun `panoramica fica dentro de 60 por cento`() {
        assertEquals(-0.6f, MusicTheory.panFor(0f, 100f), 1e-4f)
        assertEquals(0.6f, MusicTheory.panFor(100f, 100f), 1e-4f)
        assertEquals(0f, MusicTheory.panFor(50f, 100f), 1e-4f)
    }
}
