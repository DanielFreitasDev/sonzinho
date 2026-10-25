package dev.danielfreitas.sonzinho.domain.music

import dev.danielfreitas.sonzinho.domain.model.Instrument
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale

/** Lógica musical pura (sem Android): escalas, graus e mapeamento de posição para nota. */
object MusicTheory {
    /** Todas as notas MIDI da escala dentro de [low, high], em ordem crescente. */
    fun scaleNotes(
        scale: Scale,
        key: MusicKey,
        low: Int,
        high: Int,
    ): List<Int> =
        (low..high).filter { midi ->
            val pitchClass = ((midi - key.semitone) % 12 + 12) % 12
            pitchClass in scale.intervals
        }

    /**
     * Nota do [degree]-ésimo grau acima da tônica mais próxima de [baseMidi] (graus além do
     * tamanho da escala sobem de oitava; negativos descem).
     */
    fun noteForDegree(
        scale: Scale,
        key: MusicKey,
        baseMidi: Int,
        degree: Int,
    ): Int {
        val size = scale.intervals.size
        val tonic = baseMidi - (((baseMidi - key.semitone) % 12 + 12) % 12)
        val octave = Math.floorDiv(degree, size)
        val index = Math.floorMod(degree, size)
        return tonic + 12 * octave + scale.intervals[index]
    }

    /** Notas da escala que soam bonitas para o instrumento, limitadas a [maxNotes] centrais. */
    fun playableNotes(
        scale: Scale,
        key: MusicKey,
        instrument: Instrument,
        maxNotes: Int,
    ): List<Int> {
        val all = scaleNotes(scale, key, instrument.lowestMidi, instrument.highestMidi)
        if (all.size <= maxNotes) return all
        val start = (all.size - maxNotes) / 2
        return all.subList(start, start + maxNotes)
    }

    /**
     * Região da tela mágica: o grave fica embaixo/à esquerda e o agudo em cima/à direita.
     * A diagonal pesa mais o eixo horizontal (a tela fica deitada, em paisagem).
     */
    fun magicRegion(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        regions: Int,
    ): Int {
        if (regions <= 1 || width <= 0f || height <= 0f) return 0
        val nx = (x / width).coerceIn(0f, 1f)
        val ny = 1f - (y / height).coerceIn(0f, 1f)
        val value = nx * HORIZONTAL_WEIGHT + ny * (1f - HORIZONTAL_WEIGHT)
        return (value * regions).toInt().coerceIn(0, regions - 1)
    }

    /** Panorâmica estéreo suave a partir da posição horizontal (-0,6 .. 0,6). */
    fun panFor(
        x: Float,
        width: Float,
    ): Float = if (width <= 0f) 0f else ((x / width).coerceIn(0f, 1f) * 2f - 1f) * MAX_PAN

    /** Velocidade de um toque: dedos e palmas maiores tocam um pouco mais forte, sem exagero. */
    fun velocityForTouch(
        pressure: Float,
        sizeFraction: Float,
    ): Float {
        val p = if (pressure.isNaN() || pressure <= 0f) 0.6f else pressure.coerceIn(0.2f, 1f)
        val s = sizeFraction.coerceIn(0f, 1f)
        return (0.55f + 0.25f * p + 0.15f * s).coerceIn(0.45f, 0.92f)
    }

    private const val HORIZONTAL_WEIGHT = 0.68f
    private const val MAX_PAN = 0.6f
}
