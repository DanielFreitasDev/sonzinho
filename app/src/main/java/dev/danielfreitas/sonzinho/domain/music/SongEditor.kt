package dev.danielfreitas.sonzinho.domain.music

import dev.danielfreitas.sonzinho.domain.model.Cell
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.model.TrackKind

/** Edições imutáveis de uma música do sequenciador. */
object SongEditor {
    fun toggleCell(
        song: Song,
        kind: TrackKind,
        step: Int,
        row: Int,
    ): Song {
        if (step !in 0 until song.steps || row !in 0 until Song.ROWS) return song
        return updateTrack(song, kind) { track ->
            val cell = Cell(step, row)
            val cells =
                when {
                    cell in track.cells -> track.cells - cell
                    // O grave toca uma nota por vez: escolher outra altura substitui a anterior.
                    kind == TrackKind.BASS -> track.cells.filterNot { it.step == step }.toSet() + cell
                    else -> track.cells + cell
                }
            track.copy(cells = cells)
        }
    }

    fun setTempo(
        song: Song,
        bpm: Int,
    ): Song = song.copy(tempoBpm = bpm.coerceIn(Song.tempoRange))

    /** Ao reduzir os passos, as notas além do fim são descartadas. */
    fun setSteps(
        song: Song,
        steps: Int,
    ): Song {
        val valid = if (steps in Song.stepOptions) steps else song.steps
        return song.copy(
            steps = valid,
            tracks = song.tracks.map { t -> t.copy(cells = t.cells.filter { it.step < valid }.toSet()) },
        )
    }

    fun setInstrument(
        song: Song,
        kind: TrackKind,
        instrumentId: Int,
    ): Song = updateTrack(song, kind) { it.copy(instrumentId = instrumentId) }

    fun toggleMute(
        song: Song,
        kind: TrackKind,
    ): Song = updateTrack(song, kind) { it.copy(muted = !it.muted) }

    fun clearTrack(
        song: Song,
        kind: TrackKind,
    ): Song = updateTrack(song, kind) { it.copy(cells = emptySet()) }

    /** Uma musiquinha de exemplo para ninguém começar com a grade vazia. */
    fun demoSong(name: String = "Primeira musiquinha"): Song {
        var song = Song(name = name)
        val melody = listOf(0 to 2, 2 to 3, 4 to 4, 6 to 3, 8 to 5, 10 to 4, 12 to 2, 14 to 0)
        melody.forEach { (step, row) -> song = toggleCell(song, TrackKind.MELODY, step, row) }
        listOf(0 to 0, 4 to 3, 8 to 1, 12 to 3).forEach { (step, row) -> song = toggleCell(song, TrackKind.BASS, step, row) }
        listOf(3 to 5, 11 to 7).forEach { (step, row) -> song = toggleCell(song, TrackKind.SPARKLE, step, row) }
        listOf(0 to 2, 8 to 1).forEach { (step, row) -> song = toggleCell(song, TrackKind.PAD, step, row) }
        return song
    }

    private inline fun updateTrack(
        song: Song,
        kind: TrackKind,
        transform: (dev.danielfreitas.sonzinho.domain.model.Track) -> dev.danielfreitas.sonzinho.domain.model.Track,
    ): Song = song.copy(tracks = song.tracks.map { if (it.kind == kind) transform(it) else it })
}
