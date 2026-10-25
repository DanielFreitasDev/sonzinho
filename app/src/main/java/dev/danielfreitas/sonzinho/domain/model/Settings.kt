package dev.danielfreitas.sonzinho.domain.model

enum class ScreenOrientation(
    val displayName: String,
) {
    LANDSCAPE("Paisagem (recomendado)"),
    REVERSE_LANDSCAPE("Paisagem invertida"),
    PORTRAIT("Retrato"),
}

enum class AnimationLevel(
    val displayName: String,
    val particleScale: Float,
    val motionScale: Float,
) {
    CALM("Calminha", 0.45f, 0.6f),
    NORMAL("Normal", 1f, 1f),
    PLAYFUL("Animada", 1.5f, 1.25f),
}

/** Brincadeira mostrada ao bebê. Quem escolhe são os pais; o bebê nunca vê menus. */
enum class BabyActivity(
    val displayName: String,
    val description: String,
) {
    MAGIC_SCREEN("Tela mágica", "A tela toda vira instrumento"),
    ANIMALS("Bichinhos musicais", "Bichinhos que cantam e pulam"),
    KEYBOARD("Teclado colorido", "Poucas teclas gigantes"),
    AUTO_MUSIC("Música que toca sozinha", "Suas músicas com animação"),
}

enum class AnimalSoundMode(
    val displayName: String,
) {
    VOICES("Vozes de bichinho"),
    NOTES("Notas musicais"),
}

data class AppSettings(
    val orientation: ScreenOrientation = ScreenOrientation.LANDSCAPE,
    /** Teto do volume do app (0..1). Padrão conservador: metade. */
    val maxVolume: Float = DEFAULT_MAX_VOLUME,
    val instrumentId: Int = Instrument.MARIMBA.id,
    val scale: Scale = Scale.PENTATONIC_MAJOR,
    val key: MusicKey = MusicKey.C,
    val animationLevel: AnimationLevel = AnimationLevel.NORMAL,
    val vibration: Boolean = false,
    /** Minutos até o aviso de descanso (0 = desligado). */
    val usageReminderMinutes: Int = 0,
    val lockModeEnabled: Boolean = true,
    val babyActivity: BabyActivity = BabyActivity.MAGIC_SCREEN,
    val animalSoundMode: AnimalSoundMode = AnimalSoundMode.VOICES,
    val keyboardKeys: Int = 6,
    val reverb: Float = 0.9f,
    val hasPin: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAX_VOLUME = 0.5f
        const val MIN_VOLUME = 0.1f
        val reminderOptions = listOf(0, 10, 15, 20, 30, 45)
        val keyboardKeyRange = 5..8
    }
}
