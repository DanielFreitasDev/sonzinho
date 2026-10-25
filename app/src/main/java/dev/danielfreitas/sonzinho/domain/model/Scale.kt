package dev.danielfreitas.sonzinho.domain.model

/** Escalas oferecidas aos pais. A pentatônica maior é o padrão: qualquer combinação soa bem. */
enum class Scale(
    val displayName: String,
    val description: String,
    val intervals: List<Int>,
) {
    PENTATONIC_MAJOR("Pentatônica alegre", "Padrão: tudo combina com tudo", listOf(0, 2, 4, 7, 9)),
    PENTATONIC_MINOR("Pentatônica suave", "Mais calminha e misteriosa", listOf(0, 3, 5, 7, 10)),
    JAPANESE("Jardim japonês", "Delicada, como sinos de vento", listOf(0, 2, 3, 7, 8)),
    MAJOR("Maior", "Dó-ré-mi completo", listOf(0, 2, 4, 5, 7, 9, 11)),
    LYDIAN("Sonho (lídio)", "Brilhante e flutuante", listOf(0, 2, 4, 6, 7, 9, 11)),
    DORIAN("Dórico", "Suave, com um toque de saudade", listOf(0, 2, 3, 5, 7, 9, 10)),
    WHOLE_TONE("Nuvem (tons inteiros)", "Etérea, sem começo nem fim", listOf(0, 2, 4, 6, 8, 10)),
}

/** Tonalidade (nota base). */
enum class MusicKey(
    val displayName: String,
    val semitone: Int,
) {
    C("Dó", 0),
    C_SHARP("Dó♯", 1),
    D("Ré", 2),
    E_FLAT("Mi♭", 3),
    E("Mi", 4),
    F("Fá", 5),
    F_SHARP("Fá♯", 6),
    G("Sol", 7),
    A_FLAT("Lá♭", 8),
    A("Lá", 9),
    B_FLAT("Si♭", 10),
    B("Si", 11),
}
