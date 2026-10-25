package dev.danielfreitas.sonzinho.domain.audio

import kotlinx.coroutines.flow.StateFlow

data class SequenceEvent(
    val timeSeconds: Double,
    val instrumentId: Int,
    val midi: Float,
    val velocity: Float,
    val durationSeconds: Float,
)

/** Sequência entregue ao motor: tocada com precisão de amostra na thread de áudio. */
data class SequenceData(
    val id: Int,
    val lengthSeconds: Double,
    val loop: Boolean,
    val events: List<SequenceEvent>,
)

data class EngineSettings(
    val maxVolume: Float,
    val reverb: Float,
    val polyphony: Int = DEFAULT_POLYPHONY,
    val brightness: Float = 1f,
) {
    companion object {
        const val DEFAULT_POLYPHONY = 24
    }
}

data class EngineState(
    val loadedInstruments: Int = 0,
    val totalInstruments: Int = 0,
    val running: Boolean = false,
) {
    val loading: Boolean get() = loadedInstruments < totalInstruments
}

data class AudioDiagnostics(
    val latencyMillis: Double,
    val sampleRate: Int,
    val framesPerBurst: Int,
    val mmap: Boolean,
    val activeVoices: Int,
)

/** Porta de saída de áudio do domínio. A implementação real usa o motor nativo (Oboe). */
@Suppress("TooManyFunctions")
interface AudioEngine {
    val state: StateFlow<EngineState>

    fun start()

    fun stop()

    fun noteOn(
        instrumentId: Int,
        midi: Float,
        velocity: Float,
        pan: Float = 0f,
        durationSeconds: Float = 0f,
    ): Int

    fun noteOff(voice: Int)

    fun setVoiceTarget(
        voice: Int,
        midi: Float,
        brightness: Float,
    )

    fun allNotesOff()

    fun definePatch(
        slot: Int,
        params: FloatArray,
    )

    fun configure(settings: EngineSettings)

    fun setSequence(sequence: SequenceData)

    fun playSequence(fromStart: Boolean)

    fun stopSequence()

    fun sequencePositionSeconds(): Double

    fun isSequencePlaying(): Boolean

    fun setMuted(muted: Boolean)

    fun diagnostics(): AudioDiagnostics

    /** Instrumento a carregar primeiro (o escolhido pelos pais), para soar logo na abertura. */
    fun preferInstrument(instrumentId: Int) = Unit
}
