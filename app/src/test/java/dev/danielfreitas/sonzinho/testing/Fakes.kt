package dev.danielfreitas.sonzinho.testing

import dev.danielfreitas.sonzinho.domain.audio.AudioDiagnostics
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.audio.EngineSettings
import dev.danielfreitas.sonzinho.domain.audio.EngineState
import dev.danielfreitas.sonzinho.domain.audio.SequenceData
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
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayedNote(
    val instrumentId: Int,
    val midi: Float,
    val velocity: Float,
)

/** Motor falso: registra tudo o que seria tocado. */
class FakeAudioEngine : AudioEngine {
    val notes = mutableListOf<PlayedNote>()
    val sequences = mutableListOf<SequenceData>()
    val patches = mutableMapOf<Int, FloatArray>()
    var configured: EngineSettings? = null
    var playing = false
    var position = 0.0
    var mutedState = false
    private var nextVoice = 1
    override val state: StateFlow<EngineState> = MutableStateFlow(EngineState())

    override fun start() = Unit

    override fun stop() = Unit

    override fun noteOn(
        instrumentId: Int,
        midi: Float,
        velocity: Float,
        pan: Float,
        durationSeconds: Float,
    ): Int {
        notes += PlayedNote(instrumentId, midi, velocity)
        return nextVoice++
    }

    override fun noteOff(voice: Int) = Unit

    override fun setVoiceTarget(
        voice: Int,
        midi: Float,
        brightness: Float,
    ) = Unit

    override fun allNotesOff() = Unit

    override fun definePatch(
        slot: Int,
        params: FloatArray,
    ) {
        patches[slot] = params
    }

    override fun configure(settings: EngineSettings) {
        configured = settings
    }

    override fun setSequence(sequence: SequenceData) {
        sequences += sequence
    }

    override fun playSequence(fromStart: Boolean) {
        playing = true
    }

    override fun stopSequence() {
        playing = false
    }

    override fun sequencePositionSeconds(): Double = position

    override fun isSequencePlaying(): Boolean = playing

    override fun setMuted(muted: Boolean) {
        mutedState = muted
    }

    override fun diagnostics() = AudioDiagnostics(4.2, 48000, 96, true, 0)
}

@Suppress("TooManyFunctions")
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(onboardingDone = true),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()
    var pin: String? = null

    override suspend fun setOrientation(value: ScreenOrientation) = state.update { it.copy(orientation = value) }

    override suspend fun setMaxVolume(value: Float) = state.update { it.copy(maxVolume = value) }

    override suspend fun setInstrument(instrumentId: Int) = state.update { it.copy(instrumentId = instrumentId) }

    override suspend fun setScale(value: Scale) = state.update { it.copy(scale = value) }

    override suspend fun setKey(value: MusicKey) = state.update { it.copy(key = value) }

    override suspend fun setAnimationLevel(value: AnimationLevel) = state.update { it.copy(animationLevel = value) }

    override suspend fun setVibration(enabled: Boolean) = state.update { it.copy(vibration = enabled) }

    override suspend fun setUsageReminder(minutes: Int) = state.update { it.copy(usageReminderMinutes = minutes) }

    override suspend fun setLockMode(enabled: Boolean) = state.update { it.copy(lockModeEnabled = enabled) }

    override suspend fun setBabyActivity(value: BabyActivity) = state.update { it.copy(babyActivity = value) }

    override suspend fun setAnimalSoundMode(value: AnimalSoundMode) = state.update { it.copy(animalSoundMode = value) }

    override suspend fun setKeyboardKeys(count: Int) = state.update { it.copy(keyboardKeys = count) }

    override suspend fun setReverb(value: Float) = state.update { it.copy(reverb = value) }

    override suspend fun setOnboardingDone(done: Boolean) = state.update { it.copy(onboardingDone = done) }

    override suspend fun setPin(pin: String?) {
        this.pin = pin
        state.update { it.copy(hasPin = pin != null) }
    }

    override suspend fun verifyPin(pin: String): Boolean = pin == this.pin

    override suspend fun clearAll() = state.update { AppSettings() }
}

@Suppress("TooManyFunctions")
class FakeLibraryRepository : LibraryRepository {
    private val songState = MutableStateFlow<List<Song>>(emptyList())
    private val soundState = MutableStateFlow<List<CustomSound>>(emptyList())
    private val recordingState = MutableStateFlow<List<Recording>>(emptyList())
    override val songs: StateFlow<List<Song>> = songState
    override val sounds: StateFlow<List<CustomSound>> = soundState
    override val recordings: StateFlow<List<Recording>> = recordingState
    private var nextId = 1L

    override suspend fun saveSong(song: Song): Long {
        val id = if (song.id == 0L) nextId++ else song.id
        songState.update { list -> list.filterNot { it.id == id } + song.copy(id = id) }
        return id
    }

    override suspend fun deleteSong(id: Long) = songState.update { l -> l.filterNot { it.id == id } }

    override suspend fun saveSound(sound: CustomSound): Long {
        val id = if (sound.id == 0L) nextId++ else sound.id
        soundState.update { list -> list.filterNot { it.id == id } + sound.copy(id = id) }
        return id
    }

    override suspend fun deleteSound(id: Long) = soundState.update { l -> l.filterNot { it.id == id } }

    override suspend fun saveRecording(recording: Recording): Long {
        val id = if (recording.id == 0L) nextId++ else recording.id
        recordingState.update { list -> list.filterNot { it.id == id } + recording.copy(id = id) }
        return id
    }

    override suspend fun deleteRecording(id: Long) = recordingState.update { l -> l.filterNot { it.id == id } }

    override suspend fun renameSong(
        id: Long,
        name: String,
    ) = songState.update { l -> l.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun renameSound(
        id: Long,
        name: String,
    ) = soundState.update { l -> l.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun renameRecording(
        id: Long,
        name: String,
    ) = recordingState.update { l -> l.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun setSongVisible(
        id: Long,
        visible: Boolean,
    ) = songState.update { l -> l.map { if (it.id == id) it.copy(showInBaby = visible) else it } }

    override suspend fun setSoundVisible(
        id: Long,
        visible: Boolean,
    ) = soundState.update { l -> l.map { if (it.id == id) it.copy(showInBaby = visible) else it } }

    override suspend fun setRecordingVisible(
        id: Long,
        visible: Boolean,
    ) = recordingState.update { l -> l.map { if (it.id == id) it.copy(showInBaby = visible) else it } }

    override suspend fun clearAll() {
        songState.value = emptyList()
        soundState.value = emptyList()
        recordingState.value = emptyList()
    }
}
