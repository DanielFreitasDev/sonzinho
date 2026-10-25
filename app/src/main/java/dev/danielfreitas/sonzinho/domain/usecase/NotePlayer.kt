package dev.danielfreitas.sonzinho.domain.usecase

import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.model.RecordedNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Relógio monotônico injetável (testes usam um relógio falso). */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

/**
 * Gravação livre: guarda as notas tocadas (não o áudio), o que deixa as gravações leves e
 * permite tocá-las de novo pelo motor com o mesmo som.
 */
class FreePlayRecorder(
    private val clock: MonotonicClock,
) {
    private val notes = mutableListOf<RecordedNote>()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private var startedAt = 0L

    @Synchronized
    fun start() {
        notes.clear()
        startedAt = clock.nowMillis()
        _recording.value = true
    }

    @Synchronized
    fun record(
        instrumentId: Int,
        midi: Float,
        velocity: Float,
        durationSeconds: Float,
    ) {
        if (!_recording.value) return
        if (notes.size >= MAX_NOTES) return
        notes +=
            RecordedNote(
                timeMs = clock.nowMillis() - startedAt,
                instrumentId = instrumentId,
                midi = midi,
                velocity = velocity,
                durationMs = (durationSeconds * 1000).toLong(),
            )
    }

    /** Encerra e devolve as notas e a duração total (ms). */
    @Synchronized
    fun stop(): Pair<List<RecordedNote>, Long> {
        val duration = if (_recording.value) clock.nowMillis() - startedAt else 0L
        _recording.value = false
        return notes.toList() to duration
    }

    @Synchronized
    fun elapsedMillis(): Long = if (_recording.value) clock.nowMillis() - startedAt else 0L

    companion object {
        const val MAX_NOTES = 20_000
    }
}

/** Ponto único por onde todo toque do bebê ou dos pais vira som (e entra na gravação, se ativa). */
@Singleton
class NotePlayer
    @Inject
    constructor(
        private val engine: AudioEngine,
        val recorder: FreePlayRecorder,
    ) {
        fun play(
            instrumentId: Int,
            midi: Float,
            velocity: Float,
            pan: Float = 0f,
            durationSeconds: Float = 0f,
        ): Int {
            recorder.record(instrumentId, midi, velocity, durationSeconds)
            return engine.noteOn(instrumentId, midi, velocity, pan, durationSeconds)
        }

        fun release(voice: Int) = engine.noteOff(voice)

        fun glide(
            voice: Int,
            midi: Float,
            brightness: Float,
        ) = engine.setVoiceTarget(voice, midi, brightness)
    }
