package dev.danielfreitas.sonzinho.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.danielfreitas.sonzinho.domain.audio.AudioDiagnostics
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.model.AnimalSoundMode
import dev.danielfreitas.sonzinho.domain.model.AnimationLevel
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.ScreenOrientation
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.music.MusicTheory
import dev.danielfreitas.sonzinho.domain.music.SongScheduler
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.FreePlayRecorder
import dev.danielfreitas.sonzinho.domain.usecase.PinHasher
import dev.danielfreitas.sonzinho.ui.CustomSoundSlots
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val library: LibraryRepository,
        private val engine: AudioEngine,
        private val recorder: FreePlayRecorder,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
        val babyRecording: StateFlow<Boolean> = recorder.recording

        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        fun consumeMessage() {
            _message.value = null
        }

        private fun edit(block: suspend SettingsRepository.() -> Unit) {
            viewModelScope.launch { repository.block() }
        }

        fun setOrientation(v: ScreenOrientation) = edit { setOrientation(v) }

        fun setMaxVolume(v: Float) = edit { setMaxVolume(v) }

        fun setInstrument(id: Int) = edit { setInstrument(id) }

        fun setScale(v: Scale) = edit { setScale(v) }

        fun setKey(v: MusicKey) = edit { setKey(v) }

        fun setAnimationLevel(v: AnimationLevel) = edit { setAnimationLevel(v) }

        fun setVibration(v: Boolean) = edit { setVibration(v) }

        fun setUsageReminder(v: Int) = edit { setUsageReminder(v) }

        fun setLockMode(v: Boolean) = edit { setLockMode(v) }

        fun setBabyActivity(v: BabyActivity) = edit { setBabyActivity(v) }

        fun setAnimalSoundMode(v: AnimalSoundMode) = edit { setAnimalSoundMode(v) }

        fun setKeyboardKeys(v: Int) = edit { setKeyboardKeys(v) }

        fun setReverb(v: Float) = edit { setReverb(v) }

        /** Toca um arpejo curto para os pais conferirem volume e timbre. */
        fun testSound() {
            val s = settings.value
            val instrument = dev.danielfreitas.sonzinho.domain.model.Instrument.fromId(s.instrumentId)
                ?: dev.danielfreitas.sonzinho.domain.model.Instrument.MARIMBA
            val notes = MusicTheory.playableNotes(s.scale, s.key, instrument, 8)
            val events =
                notes.take(6).mapIndexed { i, midi ->
                    dev.danielfreitas.sonzinho.domain.audio.SequenceEvent(i * 0.18, instrument.id, midi.toFloat(), 0.75f, 0.6f)
                }
            engine.setSequence(dev.danielfreitas.sonzinho.domain.audio.SequenceData(TEST_ID, 2.0, loop = false, events = events))
            engine.playSequence(fromStart = true)
        }

        fun setPin(pin: String): Boolean {
            if (!PinHasher.isValidPin(pin)) return false
            edit { setPin(pin) }
            _message.value = "PIN salvo"
            return true
        }

        fun removePin() {
            edit { setPin(null) }
            _message.value = "PIN removido: o desafio volta a ser uma conta"
        }

        fun clearAllData() {
            viewModelScope.launch {
                engine.stopSequence()
                library.clearAll()
                repository.clearAll()
                repository.setOnboardingDone(true)
                _message.value = "Todos os dados foram apagados"
            }
        }

        fun diagnostics(): AudioDiagnostics = engine.diagnostics()

        /** Grava a brincadeira do bebê: as notas tocadas a partir de agora. */
        fun startBabyRecording() = recorder.start()

        fun stopBabyRecording() {
            val (notes, duration) = recorder.stop()
            if (notes.isEmpty()) {
                _message.value = "Nada foi tocado durante a gravação"
                return
            }
            val name = "Brincadeira de " + SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date())
            viewModelScope.launch {
                library.saveRecording(Recording(name = name, durationMs = duration, notes = notes))
                _message.value = "Gravação salva em Minhas músicas e sons"
            }
        }

        private companion object {
            const val TEST_ID = 424_242
        }
    }

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val sounds: List<CustomSound> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    val soundSlots: Map<Long, Int> = emptyMap(),
)

@HiltViewModel
@Suppress("TooManyFunctions")
class LibraryViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val engine: AudioEngine,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

        val state: StateFlow<LibraryState> =
            combine(library.songs, library.sounds, library.recordings) { songs, sounds, recordings ->
                LibraryState(songs, sounds, recordings, CustomSoundSlots.assign(sounds))
            }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryState())

        private val _playingKey = MutableStateFlow<String?>(null)
        val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

        fun playSong(song: Song) {
            engine.setSequence(SongScheduler.toSequence(song))
            engine.playSequence(fromStart = true)
            _playingKey.value = "song-${song.id}"
        }

        fun playRecording(recording: Recording) {
            engine.setSequence(SongScheduler.toSequence(recording, RECORDING_OFFSET + recording.id.toInt()))
            engine.playSequence(fromStart = true)
            _playingKey.value = "rec-${recording.id}"
        }

        fun playSound(sound: CustomSound) {
            val slot = state.value.soundSlots[sound.id] ?: return
            val s = settings.value
            val notes = MusicTheory.scaleNotes(s.scale, s.key, 55, 79)
            val events =
                listOf(0, 2, 4, 2).mapIndexed { i, idx ->
                    dev.danielfreitas.sonzinho.domain.audio.SequenceEvent(i * 0.3, slot, notes[idx % notes.size].toFloat(), 0.8f, 0.4f)
                }
            engine.setSequence(dev.danielfreitas.sonzinho.domain.audio.SequenceData(SOUND_OFFSET + sound.id.toInt(), 2.4, false, events))
            engine.playSequence(fromStart = true)
            _playingKey.value = "sound-${sound.id}"
        }

        fun stop() {
            engine.stopSequence()
            engine.allNotesOff()
            _playingKey.value = null
        }

        fun renameSong(
            id: Long,
            name: String,
        ) = launch { library.renameSong(id, name) }

        fun renameSound(
            id: Long,
            name: String,
        ) = launch { library.renameSound(id, name) }

        fun renameRecording(
            id: Long,
            name: String,
        ) = launch { library.renameRecording(id, name) }

        fun deleteSong(id: Long) = launch { library.deleteSong(id) }

        fun deleteSound(id: Long) = launch { library.deleteSound(id) }

        fun deleteRecording(id: Long) = launch { library.deleteRecording(id) }

        fun setSongVisible(
            id: Long,
            v: Boolean,
        ) = launch { library.setSongVisible(id, v) }

        fun setSoundVisible(
            id: Long,
            v: Boolean,
        ) = launch { library.setSoundVisible(id, v) }

        fun setRecordingVisible(
            id: Long,
            v: Boolean,
        ) = launch { library.setRecordingVisible(id, v) }

        override fun onCleared() {
            engine.stopSequence()
        }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private companion object {
            const val RECORDING_OFFSET = 1_000_000
            const val SOUND_OFFSET = 2_000_000
        }
    }
