package dev.danielfreitas.sonzinho.data

import dev.danielfreitas.sonzinho.data.audio.SampleLoader
import dev.danielfreitas.sonzinho.data.db.LibraryMapper
import dev.danielfreitas.sonzinho.data.db.SongEntity
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.RecordedNote
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.SoundShape
import dev.danielfreitas.sonzinho.domain.model.TrackKind
import dev.danielfreitas.sonzinho.domain.music.SongEditor
import dev.danielfreitas.sonzinho.ui.CustomSoundSlots
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLayerTest {
    @Test
    fun `musica sobrevive ida e volta pelo banco`() {
        val song = SongEditor.demoSong().copy(id = 7, scale = Scale.JAPANESE, key = MusicKey.F, tempoBpm = 80)
        val back = LibraryMapper.toSong(LibraryMapper.fromSong(song))
        assertEquals(song.copy(updatedAt = back.updatedAt), back)
    }

    @Test
    fun `dados corrompidos viram musica vazia valida`() {
        val entity = SongEntity(1, "x", 999, 13, "NAO_EXISTE", "??", "{lixo", true, 0, 0)
        val song = LibraryMapper.toSong(entity)
        assertEquals(160, song.tempoBpm)
        assertEquals(16, song.steps)
        assertEquals(Scale.PENTATONIC_MAJOR, song.scale)
        assertEquals(TrackKind.entries.size, song.tracks.size)
        assertTrue(song.isEmpty)
    }

    @Test
    fun `som e gravacao sobrevivem ida e volta`() {
        val sound = CustomSound(3, "Bolha", SoundParams(SoundShape.BELL, 0.2f, 0.9f, 0.1f, 0.5f, 0.3f), 4, false)
        assertEquals(sound, LibraryMapper.toSound(LibraryMapper.fromSound(sound)))
        val rec = Recording(5, "R", 1200, listOf(RecordedNote(10, 1, 60.5f, 0.7f, 200)), true, 99)
        assertEquals(rec, LibraryMapper.toRecording(LibraryMapper.fromRecording(rec)))
    }

    @Test
    fun `slots dos sons criados sao estaveis e limitados`() {
        val sounds = (1L..80L).map { CustomSound(it, "s$it", SoundParams()) }.shuffled()
        val slots = CustomSoundSlots.assign(sounds)
        assertEquals(Instrument.FIRST_CUSTOM_SLOT, slots[1L])
        assertEquals(62, slots.size) // 64..125
        assertTrue(slots.values.all { it in 64..125 })
        assertTrue(Instrument.CREATOR_PREVIEW_SLOT !in slots.values)
    }

    @Test
    fun `silencio do inicio da amostra e removido sem cortar o ataque`() {
        val pcm = shortArrayOf(0, 0, 1, -2, 0, 0, 500, 400, 300, 200)
        val trimmed = SampleLoader.trimLeadingSilence(pcm, channels = 2)
        assertArrayEquals(shortArrayOf(500, 400, 300, 200), trimmed)
        val loud = shortArrayOf(900, 800)
        assertArrayEquals(loud, SampleLoader.trimLeadingSilence(loud, channels = 1))
    }
}
