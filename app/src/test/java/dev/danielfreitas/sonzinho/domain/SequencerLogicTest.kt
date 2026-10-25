package dev.danielfreitas.sonzinho.domain

import dev.danielfreitas.sonzinho.domain.model.Cell
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.RecordedNote
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.model.TrackKind
import dev.danielfreitas.sonzinho.domain.music.SongEditor
import dev.danielfreitas.sonzinho.domain.music.SongScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencerLogicTest {
    private val empty = Song(name = "teste", tempoBpm = 120, steps = 16)

    @Test
    fun `alternar celula liga e desliga`() {
        val on = SongEditor.toggleCell(empty, TrackKind.MELODY, 3, 2)
        assertTrue(Cell(3, 2) in on.tracks.first { it.kind == TrackKind.MELODY }.cells)
        val off = SongEditor.toggleCell(on, TrackKind.MELODY, 3, 2)
        assertTrue(off.isEmpty)
    }

    @Test
    fun `melodia aceita acordes mas o grave toca uma nota por passo`() {
        var s = SongEditor.toggleCell(empty, TrackKind.MELODY, 0, 1)
        s = SongEditor.toggleCell(s, TrackKind.MELODY, 0, 4)
        assertEquals(2, s.tracks.first { it.kind == TrackKind.MELODY }.cells.size)
        var b = SongEditor.toggleCell(empty, TrackKind.BASS, 0, 1)
        b = SongEditor.toggleCell(b, TrackKind.BASS, 0, 4)
        assertEquals(setOf(Cell(0, 4)), b.tracks.first { it.kind == TrackKind.BASS }.cells)
    }

    @Test
    fun `celulas fora da grade sao ignoradas`() {
        assertTrue(SongEditor.toggleCell(empty, TrackKind.MELODY, 16, 0).isEmpty)
        assertTrue(SongEditor.toggleCell(empty, TrackKind.MELODY, 0, Song.ROWS).isEmpty)
    }

    @Test
    fun `reduzir para 8 passos descarta notas do fim`() {
        var s = SongEditor.toggleCell(empty, TrackKind.MELODY, 2, 0)
        s = SongEditor.toggleCell(s, TrackKind.MELODY, 12, 0)
        val short = SongEditor.setSteps(s, 8)
        assertEquals(8, short.steps)
        assertEquals(setOf(Cell(2, 0)), short.tracks.first { it.kind == TrackKind.MELODY }.cells)
        assertEquals(16, SongEditor.setSteps(s, 12).steps) // valor inválido é ignorado
    }

    @Test
    fun `andamento fica entre 60 e 160`() {
        assertEquals(60, SongEditor.setTempo(empty, 10).tempoBpm)
        assertEquals(160, SongEditor.setTempo(empty, 999).tempoBpm)
    }

    @Test
    fun `sequencia tem tempos exatos de colcheia`() {
        var s = SongEditor.toggleCell(empty, TrackKind.MELODY, 0, 0)
        s = SongEditor.toggleCell(s, TrackKind.MELODY, 4, 2)
        s = SongEditor.toggleCell(s, TrackKind.BASS, 8, 0)
        val seq = SongScheduler.toSequence(s)
        // 120 bpm: colcheia = 0,25 s; 16 passos = 4 s.
        assertEquals(4.0, seq.lengthSeconds, 1e-9)
        assertTrue(seq.loop)
        assertEquals(listOf(0.0, 1.0, 2.0), seq.events.map { it.timeSeconds })
        assertEquals(Instrument.BASS.id, seq.events[2].instrumentId)
        assertEquals(36f, seq.events[2].midi) // grau 0 do grave em Dó
        assertTrue(seq.events.all { it.velocity in 0.3f..1f && it.durationSeconds > 0f })
    }

    @Test
    fun `linha calada nao toca`() {
        var s = SongEditor.toggleCell(empty, TrackKind.SPARKLE, 1, 1)
        s = SongEditor.toggleMute(s, TrackKind.SPARKLE)
        assertTrue(SongScheduler.toSequence(s).events.isEmpty())
    }

    @Test
    fun `instrumento da linha e respeitado`() {
        var s = SongEditor.toggleCell(empty, TrackKind.MELODY, 0, 0)
        s = SongEditor.setInstrument(s, TrackKind.MELODY, Instrument.HARP.id)
        assertEquals(Instrument.HARP.id, SongScheduler.toSequence(s).events.single().instrumentId)
    }

    @Test
    fun `passo atual acompanha a posicao do motor`() {
        assertEquals(0, SongScheduler.currentStep(0.0, empty))
        assertEquals(5, SongScheduler.currentStep(1.3, empty))
        assertEquals(15, SongScheduler.currentStep(99.0, empty))
    }

    @Test
    fun `gravacao vira sequencia sem loop com folga no fim`() {
        val rec =
            Recording(
                name = "g",
                durationMs = 2000,
                notes = listOf(RecordedNote(0, 1, 60f, 0.8f), RecordedNote(1500, 1, 64f, 0.7f)),
            )
        val seq = SongScheduler.toSequence(rec, 9)
        assertFalse(seq.loop)
        assertEquals(listOf(0.0, 1.5), seq.events.map { it.timeSeconds })
        assertTrue(seq.lengthSeconds > 2.0)
    }

    @Test
    fun `musica de exemplo nao e vazia e usa as quatro linhas`() {
        val demo = SongEditor.demoSong()
        assertFalse(demo.isEmpty)
        assertTrue(TrackKind.entries.all { kind -> demo.tracks.first { it.kind == kind }.cells.isNotEmpty() })
    }
}
