package dev.danielfreitas.sonzinho.ui.baby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.audio.SequenceData
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.music.SongEditor
import dev.danielfreitas.sonzinho.domain.music.SongScheduler
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.NotePlayer
import dev.danielfreitas.sonzinho.ui.CustomSoundSlots
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Som criado pelos pais que aparece no modo bebê (como bolhinha mágica). */
data class BabySound(
    val sound: CustomSound,
    val slot: Int,
)

/** O que está tocando sozinho agora. */
data class AutoMusicItem(
    val title: String,
    val sequence: SequenceData,
    val scale: Scale,
    val key: MusicKey,
    val song: Song?,
)

@HiltViewModel
class BabyViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        private val library: LibraryRepository,
        private val engine: AudioEngine,
        private val notePlayer: NotePlayer,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val babySounds: StateFlow<List<BabySound>> =
            library.sounds
                .map { sounds ->
                    val slots = CustomSoundSlots.assign(sounds)
                    sounds.filter { it.showInBaby }.mapNotNull { s -> slots[s.id]?.let { BabySound(s, it) } }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _autoItem = MutableStateFlow<AutoMusicItem?>(null)
        val autoItem: StateFlow<AutoMusicItem?> = _autoItem.asStateFlow()

        private var autoJob: Job? = null

        fun play(
            instrumentId: Int,
            midi: Float,
            velocity: Float,
            pan: Float = 0f,
            durationSeconds: Float = 0f,
        ): Int = notePlayer.play(instrumentId, midi, velocity, pan, durationSeconds)

        fun sequencePosition(): Double = engine.sequencePositionSeconds()

        fun startAutoMusic() {
            if (autoJob?.isActive == true) return
            autoJob =
                viewModelScope.launch {
                    while (isActive) {
                        val playlist = buildPlaylist()
                        for (item in playlist) {
                            _autoItem.value = item
                            engine.setSequence(item.sequence)
                            engine.playSequence(fromStart = true)
                            waitUntilDone(item)
                        }
                    }
                }
        }

        fun stopAutoMusic() {
            autoJob?.cancel()
            autoJob = null
            engine.stopSequence()
            _autoItem.value = null
        }

        override fun onCleared() {
            stopAutoMusic()
        }

        private suspend fun buildPlaylist(): List<AutoMusicItem> {
            val songs =
                library.songs
                    .first()
                    .filter { it.showInBaby && !it.isEmpty }
            val recordings = library.recordings.first().filter { it.showInBaby && it.notes.isNotEmpty() }
            val songItems =
                songs.map { AutoMusicItem(it.name, SongScheduler.toSequence(it), it.scale, it.key, it) }
            val recordingItems =
                recordings.map { r ->
                    val s = settings.value ?: AppSettings()
                    AutoMusicItem(r.name, SongScheduler.toSequence(r, RECORDING_ID_OFFSET + r.id.toInt()), s.scale, s.key, null)
                }
            val items = songItems + recordingItems
            if (items.isNotEmpty()) return items
            // Sem músicas salvas: toca a musiquinha de exemplo (a tela nunca fica vazia).
            val s = settings.value ?: AppSettings()
            val demo = SongEditor.demoSong().copy(scale = s.scale, key = s.key)
            return listOf(AutoMusicItem(demo.name, SongScheduler.toSequence(demo, DEMO_ID), demo.scale, demo.key, demo))
        }

        private suspend fun waitUntilDone(item: AutoMusicItem) {
            if (!item.sequence.loop) {
                delay(START_GRACE_MS)
                while (engine.isSequencePlaying()) delay(POLL_MS)
                delay(GAP_MS)
                return
            }
            var loops = 0
            var last = 0.0
            while (loops < LOOPS_PER_SONG) {
                delay(POLL_MS)
                val pos = engine.sequencePositionSeconds()
                if (pos + 0.05 < last) loops++
                last = pos
            }
        }

        private companion object {
            const val LOOPS_PER_SONG = 4
            const val POLL_MS = 60L
            const val START_GRACE_MS = 300L
            const val GAP_MS = 600L
            const val DEMO_ID = 999_999
            const val RECORDING_ID_OFFSET = 1_000_000
        }
    }

