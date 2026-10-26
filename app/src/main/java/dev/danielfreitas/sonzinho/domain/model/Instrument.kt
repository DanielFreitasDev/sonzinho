package dev.danielfreitas.sonzinho.domain.model

/**
 * Instrumentos do motor. Os ids são o contrato com `Engine.h` (namespace `instrument`):
 * 0..15 amostrados, 16..31 sintetizados, 32..47 bichinhos, 64..127 sons criados pelos pais.
 *
 * @property lowestMidi e [highestMidi] delimitam a região onde o instrumento soa bonito
 * (agudos estridentes ficam de fora).
 */
enum class Instrument(
    val id: Int,
    val displayName: String,
    val lowestMidi: Int,
    val highestMidi: Int,
    val kind: Kind,
) {
    PIANO(0, "Piano", 43, 91, Kind.SAMPLED),
    MARIMBA(1, "Marimba", 45, 89, Kind.SAMPLED),
    GLOCKENSPIEL(2, "Sininhos", 72, 96, Kind.SAMPLED),
    XYLOPHONE(3, "Xilofone", 65, 96, Kind.SAMPLED),
    VIBRAPHONE(4, "Vibrafone", 53, 89, Kind.SAMPLED),
    KALIMBA(5, "Kalimba", 48, 84, Kind.SAMPLED),
    HARP(6, "Harpa", 40, 91, Kind.SAMPLED),
    CHIMES(7, "Sinos", 60, 93, Kind.SAMPLED),
    KALIMBA_SOFT(8, "Kalimba doce", 55, 89, Kind.SAMPLED),
    MUSIC_BOX_SAMPLED(9, "Caixinha de música", 67, 96, Kind.SAMPLED),
    RECORDER(10, "Flauta doce", 67, 93, Kind.SAMPLED),
    PAD(16, "Nuvem", 41, 84, Kind.SYNTH),
    BASS(17, "Grave quentinho", 28, 55, Kind.SYNTH),
    BELL(18, "Sininho mágico", 60, 96, Kind.SYNTH),
    FLUTE(19, "Flautinha", 60, 91, Kind.SYNTH),
    BUBBLES(20, "Bolhinhas", 60, 91, Kind.SYNTH),
    CELESTA(21, "Celesta", 60, 96, Kind.SYNTH),
    MUSIC_BOX(22, "Caixinha mágica", 67, 96, Kind.SYNTH),
    BIRD(32, "Passarinho", 76, 93, Kind.ANIMAL),
    CAT(33, "Gatinho", 62, 79, Kind.ANIMAL),
    DUCK(34, "Patinho", 55, 72, Kind.ANIMAL),
    FROG(35, "Sapinho", 43, 60, Kind.ANIMAL),
    BEAR(36, "Ursinho", 36, 52, Kind.ANIMAL),
    WHALE(37, "Baleia", 33, 50, Kind.ANIMAL),
    ;

    enum class Kind { SAMPLED, SYNTH, ANIMAL }

    companion object {
        const val FIRST_CUSTOM_SLOT = 64
        const val CREATOR_PREVIEW_SLOT = 127

        /** Instrumentos que os pais podem escolher como padrão do modo bebê. */
        val melodic: List<Instrument> = entries.filter { it.kind != Kind.ANIMAL && it != BASS }

        fun fromId(id: Int): Instrument? = entries.firstOrNull { it.id == id }
    }
}
