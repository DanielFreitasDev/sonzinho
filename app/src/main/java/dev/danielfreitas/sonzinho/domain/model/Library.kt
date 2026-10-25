package dev.danielfreitas.sonzinho.domain.model

import kotlinx.serialization.Serializable

/** Linhas do sequenciador. Cada uma tem seu instrumento e sua região de alturas. */
@Serializable
enum class TrackKind(
    val displayName: String,
    val defaultInstrumentId: Int,
    val baseMidi: Int,
) {
    MELODY("Melodia", Instrument.MARIMBA.id, 60),
    SPARKLE("Brilhinho", Instrument.GLOCKENSPIEL.id, 72),
    BASS("Grave", Instrument.BASS.id, 36),
    PAD("Nuvem", Instrument.PAD.id, 48),
}

@Serializable
data class Cell(
    val step: Int,
    /** Grau da escala (0 = mais grave da linha). */
    val row: Int,
)

@Serializable
data class Track(
    val kind: TrackKind,
    val instrumentId: Int = kind.defaultInstrumentId,
    val cells: Set<Cell> = emptySet(),
    val muted: Boolean = false,
)

data class Song(
    val id: Long = 0,
    val name: String,
    val tempoBpm: Int = DEFAULT_TEMPO,
    val steps: Int = 16,
    val scale: Scale = Scale.PENTATONIC_MAJOR,
    val key: MusicKey = MusicKey.C,
    val tracks: List<Track> = TrackKind.entries.map { Track(it) },
    val showInBaby: Boolean = true,
    val colorIndex: Int = 0,
    val updatedAt: Long = 0,
) {
    val isEmpty: Boolean get() = tracks.all { it.cells.isEmpty() }

    companion object {
        const val DEFAULT_TEMPO = 96
        val tempoRange = 60..160
        val stepOptions = listOf(8, 16)
        const val ROWS = 8
    }
}

@Serializable
data class RecordedNote(
    val timeMs: Long,
    val instrumentId: Int,
    val midi: Float,
    val velocity: Float,
    val durationMs: Long = 0,
)

data class Recording(
    val id: Long = 0,
    val name: String,
    val durationMs: Long,
    val notes: List<RecordedNote>,
    val showInBaby: Boolean = true,
    val createdAt: Long = 0,
)

@Serializable
enum class SoundShape(
    val displayName: String,
) {
    BALL("Bolinha"),
    FLUTE("Flautinha"),
    STRING("Cordinha"),
    ROBOT("Robozinho"),
    BELL("Sino"),
    CLOUD("Nuvem"),
}

/** Som criado no "criador de sons": todos os controles vão de 0 a 1. */
@Serializable
data class SoundParams(
    val shape: SoundShape = SoundShape.BALL,
    /** 0 = bem grave, 1 = bem agudo. */
    val pitch: Float = 0.5f,
    /** 0 = macio, 1 = brilhante. */
    val brightness: Float = 0.4f,
    /** 0 = curtinho, 1 = longo. */
    val length: Float = 0.4f,
    /** 0 = sem eco, 1 = muito eco. */
    val echo: Float = 0.2f,
    /** 0 = parado, 1 = balança bastante (vibrato). */
    val wobble: Float = 0.1f,
)

data class CustomSound(
    val id: Long = 0,
    val name: String,
    val params: SoundParams,
    val colorIndex: Int = 0,
    val showInBaby: Boolean = true,
)
