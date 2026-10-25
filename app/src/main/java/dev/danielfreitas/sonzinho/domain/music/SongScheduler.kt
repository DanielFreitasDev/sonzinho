package dev.danielfreitas.sonzinho.domain.music

import dev.danielfreitas.sonzinho.domain.audio.SequenceData
import dev.danielfreitas.sonzinho.domain.audio.SequenceEvent
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.model.Track
import dev.danielfreitas.sonzinho.domain.model.TrackKind

/** Converte músicas do sequenciador e gravações livres em sequências para o motor. */
object SongScheduler {
    /** Cada passo da grade vale uma colcheia. */
    fun stepSeconds(tempoBpm: Int): Double = 60.0 / tempoBpm.coerceIn(Song.tempoRange) / 2.0

    fun midiFor(
        song: Song,
        track: Track,
        row: Int,
    ): Int = MusicTheory.noteForDegree(song.scale, song.key, track.kind.baseMidi, row)

    fun toSequence(
        song: Song,
        sequenceId: Int = song.id.toInt(),
    ): SequenceData {
        val step = stepSeconds(song.tempoBpm)
        val events =
            song.tracks
                .filterNot { it.muted }
                .flatMap { track ->
                    track.cells
                        .filter { it.step in 0 until song.steps && it.row in 0 until Song.ROWS }
                        .map { cell ->
                            val accent = if (cell.step % 4 == 0) ACCENT else 0f
                            SequenceEvent(
                                timeSeconds = cell.step * step,
                                instrumentId = track.instrumentId,
                                midi = midiFor(song, track, cell.row).toFloat(),
                                velocity = (velocityFor(track.kind) + accent).coerceAtMost(1f),
                                durationSeconds = (step * durationSteps(track.kind)).toFloat(),
                            )
                        }
                }.sortedBy { it.timeSeconds }
        return SequenceData(sequenceId, song.steps * step, loop = true, events = events)
    }

    fun toSequence(
        recording: Recording,
        sequenceId: Int,
    ): SequenceData {
        val events =
            recording.notes.map { note ->
                SequenceEvent(
                    timeSeconds = note.timeMs / 1000.0,
                    instrumentId = note.instrumentId,
                    midi = note.midi,
                    velocity = note.velocity,
                    durationSeconds = if (note.durationMs > 0) note.durationMs / 1000f else 0f,
                )
            }
        val length = (recording.durationMs.coerceAtLeast(events.maxOfOrNull { (it.timeSeconds * 1000).toLong() + 1 } ?: 1)) / 1000.0
        return SequenceData(sequenceId, length + TAIL_SECONDS, loop = false, events = events)
    }

    /** Passo atual (0..steps-1) para a animação a partir da posição do motor. */
    fun currentStep(
        positionSeconds: Double,
        song: Song,
    ): Int {
        val step = stepSeconds(song.tempoBpm)
        return ((positionSeconds / step).toInt()).coerceIn(0, song.steps - 1)
    }

    private fun velocityFor(kind: TrackKind): Float =
        when (kind) {
            TrackKind.MELODY -> 0.72f
            TrackKind.SPARKLE -> 0.55f
            TrackKind.BASS -> 0.8f
            TrackKind.PAD -> 0.5f
        }

    private fun durationSteps(kind: TrackKind): Double =
        when (kind) {
            TrackKind.MELODY -> 1.8
            TrackKind.SPARKLE -> 1.2
            TrackKind.BASS -> 1.6
            TrackKind.PAD -> 3.5
        }

    private const val ACCENT = 0.08f
    private const val TAIL_SECONDS = 1.5
}
