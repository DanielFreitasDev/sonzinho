package dev.danielfreitas.sonzinho.domain.audio

/**
 * Espelho de `SynthPatch` em `app/src/main/cpp/core/SynthPatch.h`. A ordem de [toFloatArray]
 * é o contrato com o motor nativo (33 floats).
 */
data class SynthPatch(
    val wave: Wave = Wave.SINE,
    val octave: Float = 0f,
    val detuneCents: Float = 0f,
    val subLevel: Float = 0f,
    val cutoffHz: Float = 3000f,
    val resonance: Float = 0.7f,
    val filterEnv: Float = 0f,
    val keyTrack: Float = 0.5f,
    val attack: Float = 0.005f,
    val decay: Float = 0.4f,
    val sustain: Float = 0.6f,
    val release: Float = 0.5f,
    val vibratoRate: Float = 5f,
    val vibratoDepth: Float = 0f,
    val tremoloRate: Float = 0f,
    val tremoloDepth: Float = 0f,
    val pitchStart: Float = 0f,
    val pitchMid: Float = 0f,
    val pitchMidTime: Float = 0f,
    val pitchEndTime: Float = 0f,
    val formantStart: Float = 0f,
    val formantMid: Float = 0f,
    val formantEnd: Float = 0f,
    val formantTime: Float = 0.3f,
    val formantMix: Float = 0f,
    val fmRatio: Float = 3.5f,
    val fmIndex: Float = 2f,
    val fmDecay: Float = 0.6f,
    val noise: Float = 0f,
    val gain: Float = 0.8f,
    val echoSend: Float = 0f,
    val reverbSend: Float = 0.25f,
    val maxSeconds: Float = 0f,
) {
    enum class Wave(
        val code: Int,
    ) {
        SINE(0),
        TRIANGLE(1),
        SAW(2),
        SQUARE(3),
        BELL(4),
        PAD(5),
    }

    fun toFloatArray(): FloatArray =
        floatArrayOf(
            wave.code.toFloat(), octave, detuneCents, subLevel, cutoffHz, resonance, filterEnv, keyTrack,
            attack, decay, sustain, release, vibratoRate, vibratoDepth, tremoloRate, tremoloDepth,
            pitchStart, pitchMid, pitchMidTime, pitchEndTime, formantStart, formantMid, formantEnd,
            formantTime, formantMix, fmRatio, fmIndex, fmDecay, noise, gain, echoSend, reverbSend, maxSeconds,
        )

    companion object {
        const val PARAM_COUNT = 33
    }
}
