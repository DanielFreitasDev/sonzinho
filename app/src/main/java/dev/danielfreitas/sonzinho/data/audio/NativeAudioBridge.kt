package dev.danielfreitas.sonzinho.data.audio

/**
 * Funções nativas do motor de áudio (`app/src/main/cpp/android/JniBridge.cpp`).
 * Todas retornam rápido: apenas empilham comandos para a thread de áudio.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class NativeAudioBridge {
    external fun nativeCreate(): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeStart(handle: Long): Boolean

    external fun nativeStop(handle: Long)

    external fun nativeNoteOn(
        handle: Long,
        instrument: Int,
        midi: Float,
        velocity: Float,
        pan: Float,
        duration: Float,
    ): Int

    external fun nativeNoteOff(
        handle: Long,
        voice: Int,
    )

    external fun nativeSetVoiceTarget(
        handle: Long,
        voice: Int,
        midi: Float,
        brightness: Float,
    )

    external fun nativeAllNotesOff(handle: Long)

    external fun nativeDefinePatch(
        handle: Long,
        slot: Int,
        params: FloatArray,
    )

    external fun nativeSetConfig(
        handle: Long,
        polyphony: Int,
        volume: Float,
        reverb: Float,
        brightness: Float,
    )

    external fun nativeLoadInstrument(
        handle: Long,
        slot: Int,
        gain: Float,
        release: Float,
        roots: FloatArray,
        rates: FloatArray,
        channels: IntArray,
        pcm: Array<ShortArray>,
    )

    external fun nativeIsInstrumentReady(
        handle: Long,
        slot: Int,
    ): Boolean

    external fun nativeSetSequence(
        handle: Long,
        id: Int,
        length: Double,
        loop: Boolean,
        times: DoubleArray,
        instruments: IntArray,
        midis: FloatArray,
        velocities: FloatArray,
        durations: FloatArray,
    )

    external fun nativePlaySequence(
        handle: Long,
        fromStart: Boolean,
    )

    external fun nativeStopSequence(handle: Long)

    external fun nativeSequencePosition(handle: Long): Double

    external fun nativeSequencePlaying(handle: Long): Boolean

    external fun nativeSetMuted(
        handle: Long,
        muted: Boolean,
    )

    external fun nativeLatencyMillis(handle: Long): Double

    external fun nativeSampleRate(handle: Long): Int

    external fun nativeFramesPerBurst(handle: Long): Int

    external fun nativeIsMmap(handle: Long): Boolean

    external fun nativeActiveVoices(handle: Long): Int

    companion object {
        init {
            System.loadLibrary("sonzinho_audio")
        }
    }
}
