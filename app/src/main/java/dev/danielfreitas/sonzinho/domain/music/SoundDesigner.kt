package dev.danielfreitas.sonzinho.domain.music

import dev.danielfreitas.sonzinho.domain.audio.SynthPatch
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.SoundShape
import kotlin.math.pow

/**
 * Traduz os controles sem jargão do criador de sons ("macio/brilhante", "curtinho/longo"...)
 * para os parâmetros do sintetizador. Todos os mapeamentos são limitados para que nenhum
 * som fique estridente ou estourado.
 */
object SoundDesigner {
    /** Nota de referência do criador: o eixo "grave/agudo" desloca até 2 oitavas para cada lado. */
    fun midiFor(
        params: SoundParams,
        baseMidi: Int = 60,
    ): Float = baseMidi + (params.pitch.coerceIn(0f, 1f) - 0.5f) * 48f

    fun toPatch(params: SoundParams): SynthPatch {
        val bright = params.brightness.coerceIn(0f, 1f)
        val length = params.length.coerceIn(0f, 1f)
        val echo = params.echo.coerceIn(0f, 1f)
        val wobble = params.wobble.coerceIn(0f, 1f)
        // Brilho em escala exponencial: 350 Hz (bem macio) a 5,5 kHz (brilhante, nunca áspero).
        val cutoff = 350f * (5500f / 350f).pow(bright)
        val decay = 0.12f + 2.6f * length * length
        val release = 0.08f + 1.6f * length
        val base =
            SynthPatch(
                cutoffHz = cutoff,
                resonance = 0.6f + 0.4f * bright,
                filterEnv = 0.6f + 0.8f * bright,
                keyTrack = 0.4f,
                attack = 0.004f,
                decay = decay,
                sustain = 0.25f * length,
                release = release,
                vibratoRate = 4.5f + 2f * wobble,
                vibratoDepth = 0.45f * wobble * wobble,
                echoSend = 0.55f * echo,
                reverbSend = 0.2f + 0.25f * echo,
                maxSeconds = 0.4f + 2.2f * length,
            )
        return when (params.shape) {
            SoundShape.BALL -> base.copy(wave = SynthPatch.Wave.SINE, subLevel = 0.15f, gain = 0.85f)
            SoundShape.FLUTE ->
                base.copy(
                    wave = SynthPatch.Wave.TRIANGLE,
                    attack = 0.03f,
                    sustain = 0.5f + 0.3f * length,
                    noise = 0.03f,
                    gain = 0.75f,
                )
            SoundShape.STRING -> base.copy(wave = SynthPatch.Wave.SAW, detuneCents = 6f, gain = 0.5f)
            SoundShape.ROBOT -> base.copy(wave = SynthPatch.Wave.SQUARE, cutoffHz = cutoff * 0.8f, gain = 0.45f)
            SoundShape.BELL ->
                base.copy(
                    wave = SynthPatch.Wave.BELL,
                    fmRatio = 3f + 4f * bright,
                    fmIndex = 0.6f + 1.4f * bright,
                    fmDecay = 0.2f + 0.8f * length,
                    sustain = 0f,
                    gain = 0.6f,
                )
            SoundShape.CLOUD ->
                base.copy(
                    wave = SynthPatch.Wave.PAD,
                    detuneCents = 9f,
                    attack = 0.15f + 0.3f * length,
                    sustain = 0.7f,
                    release = 0.6f + 1.4f * length,
                    gain = 0.5f,
                    reverbSend = 0.5f,
                )
        }
    }
}
