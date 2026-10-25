package dev.danielfreitas.sonzinho.ui

import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.TrackKind
import dev.danielfreitas.sonzinho.domain.usecase.FreePlayRecorder
import dev.danielfreitas.sonzinho.domain.usecase.NotePlayer
import dev.danielfreitas.sonzinho.testing.FakeAudioEngine
import dev.danielfreitas.sonzinho.testing.FakeLibraryRepository
import dev.danielfreitas.sonzinho.testing.FakeSettingsRepository
import dev.danielfreitas.sonzinho.ui.baby.BabyViewModel
import dev.danielfreitas.sonzinho.ui.parent.CreatorViewModel
import dev.danielfreitas.sonzinho.ui.parent.RecorderViewModel
import dev.danielfreitas.sonzinho.ui.parent.SequencerViewModel
import dev.danielfreitas.sonzinho.ui.parent.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val library = FakeLibraryRepository()
    private val engine = FakeAudioEngine()
    private var clock = 0L
    private val recorder = FreePlayRecorder { clock }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sequenciador mostra previa da nota e atualiza a musica tocando`() =
        runTest(dispatcher) {
            val vm = SequencerViewModel(engine, library, settings)
            vm.load(null)
            runCurrent()
            vm.clearTrack()
            vm.toggle(step = 2, row = 3)
            assertEquals(1, engine.notes.size)
            assertEquals(Instrument.MARIMBA.id, engine.notes.single().instrumentId)
            vm.togglePlay()
            assertTrue(engine.playing)
            val before = engine.sequences.size
            vm.toggle(step = 5, row = 1)
            assertEquals(before + 1, engine.sequences.size)
            assertEquals("mesmo id preserva a fase", engine.sequences[before - 1].id, engine.sequences.last().id)
            vm.save()
            runCurrent()
            assertEquals(1, library.songs.value.size)
            assertTrue(vm.state.value.saved)
            vm.togglePlay()
            assertFalse(engine.playing)
        }

    @Test
    fun `trocar a linha muda onde as notas entram`() =
        runTest(dispatcher) {
            val vm = SequencerViewModel(engine, library, settings)
            vm.load(null)
            runCurrent()
            vm.selectTrack(TrackKind.BASS)
            vm.clearTrack()
            vm.toggle(0, 0)
            assertEquals(Instrument.BASS.id, engine.notes.last().instrumentId)
            assertEquals(1, vm.state.value.song.tracks.first { it.kind == TrackKind.BASS }.cells.size)
        }

    @Test
    fun `musica sozinha sem musicas salvas toca a musiquinha de exemplo`() =
        runTest(dispatcher) {
            val vm = BabyViewModel(settings, library, engine, NotePlayer(engine, recorder))
            vm.startAutoMusic()
            runCurrent()
            assertTrue(engine.playing)
            assertEquals(1, engine.sequences.size)
            assertTrue(engine.sequences.single().events.isNotEmpty())
            assertEquals("Primeira musiquinha", vm.autoItem.value?.title)
            vm.stopAutoMusic()
            assertFalse(engine.playing)
        }

    @Test
    fun `sons visiveis viram bolhinhas no modo bebe`() =
        runTest(dispatcher) {
            library.saveSound(CustomSound(name = "visível", params = SoundParams()))
            library.saveSound(CustomSound(name = "escondido", params = SoundParams(), showInBaby = false))
            val vm = BabyViewModel(settings, library, engine, NotePlayer(engine, recorder))
            runCurrent()
            assertEquals(listOf("visível"), vm.babySounds.value.map { it.sound.name })
            assertEquals(64, vm.babySounds.value.single().slot)
        }

    @Test
    fun `toque do bebe toca e entra na gravacao quando ativa`() =
        runTest(dispatcher) {
            val player = NotePlayer(engine, recorder)
            val vm = BabyViewModel(settings, library, engine, player)
            vm.play(1, 60f, 0.8f)
            recorder.start()
            clock = 300
            vm.play(1, 64f, 0.8f)
            val (notes, _) = recorder.stop()
            assertEquals(2, engine.notes.size)
            assertEquals(1, notes.size)
            assertEquals(300L, notes.single().timeMs)
        }

    @Test
    fun `gravar salva a gravacao com nome`() =
        runTest(dispatcher) {
            val vm = RecorderViewModel(NotePlayer(engine, recorder), library, settings)
            vm.start()
            clock = 100
            vm.play(1, 60f, 0.7f, 0f)
            clock = 900
            assertTrue(vm.stop())
            vm.save("Ninar")
            runCurrent()
            val saved = library.recordings.value.single()
            assertEquals("Ninar", saved.name)
            assertEquals(900L, saved.durationMs)
        }

    @Test
    fun `criador define o patch de previa e salva o som`() =
        runTest(dispatcher) {
            val vm = CreatorViewModel(engine, library)
            vm.load(null)
            runCurrent()
            assertTrue(engine.patches.containsKey(Instrument.CREATOR_PREVIEW_SLOT))
            vm.padDown(0.9f, 0.8f)
            assertEquals(Instrument.CREATOR_PREVIEW_SLOT, engine.notes.last().instrumentId)
            vm.padUp()
            vm.save("Estrelinha")
            runCurrent()
            val sound = library.sounds.value.single()
            assertEquals("Estrelinha", sound.name)
            assertEquals(0.9f, sound.params.brightness, 1e-4f)
            assertEquals(0.8f, sound.params.pitch, 1e-4f)
        }

    @Test
    fun `configuracoes salvam pin valido e recusam invalido`() =
        runTest(dispatcher) {
            val vm = SettingsViewModel(settings, library, engine, recorder)
            assertFalse(vm.setPin("12"))
            assertTrue(vm.setPin("1357"))
            runCurrent()
            assertTrue(settings.settings.value.hasPin)
            vm.removePin()
            runCurrent()
            assertFalse(settings.settings.value.hasPin)
        }

    @Test
    fun `gravar a brincadeira do bebe salva as notas tocadas`() =
        runTest(dispatcher) {
            val vm = SettingsViewModel(settings, library, engine, recorder)
            vm.startBabyRecording()
            clock = 50
            recorder.record(1, 60f, 0.8f, 0f)
            clock = 5_000
            vm.stopBabyRecording()
            advanceTimeBy(10)
            runCurrent()
            val rec = library.recordings.value.single()
            assertTrue(rec.name.startsWith("Brincadeira de"))
            assertEquals(1, rec.notes.size)
        }
}
