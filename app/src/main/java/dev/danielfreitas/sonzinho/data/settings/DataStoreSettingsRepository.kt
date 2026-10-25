package dev.danielfreitas.sonzinho.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.danielfreitas.sonzinho.domain.model.AnimalSoundMode
import dev.danielfreitas.sonzinho.domain.model.AnimationLevel
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.ScreenOrientation
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = store.data.map(::toSettings)

        override suspend fun setOrientation(value: ScreenOrientation) = put(Keys.ORIENTATION, value.name)

        override suspend fun setMaxVolume(value: Float) = put(Keys.MAX_VOLUME, value.coerceIn(AppSettings.MIN_VOLUME, 1f))

        override suspend fun setInstrument(instrumentId: Int) = put(Keys.INSTRUMENT, instrumentId)

        override suspend fun setScale(value: Scale) = put(Keys.SCALE, value.name)

        override suspend fun setKey(value: MusicKey) = put(Keys.KEY, value.name)

        override suspend fun setAnimationLevel(value: AnimationLevel) = put(Keys.ANIMATION, value.name)

        override suspend fun setVibration(enabled: Boolean) = put(Keys.VIBRATION, enabled)

        override suspend fun setUsageReminder(minutes: Int) = put(Keys.REMINDER, minutes.coerceIn(0, 120))

        override suspend fun setLockMode(enabled: Boolean) = put(Keys.LOCK_MODE, enabled)

        override suspend fun setBabyActivity(value: BabyActivity) = put(Keys.BABY_ACTIVITY, value.name)

        override suspend fun setAnimalSoundMode(value: AnimalSoundMode) = put(Keys.ANIMAL_SOUND, value.name)

        override suspend fun setKeyboardKeys(count: Int) = put(Keys.KEYBOARD_KEYS, count.coerceIn(AppSettings.keyboardKeyRange))

        override suspend fun setReverb(value: Float) = put(Keys.REVERB, value.coerceIn(0f, 2f))

        override suspend fun setOnboardingDone(done: Boolean) = put(Keys.ONBOARDING, done)

        override suspend fun setPin(pin: String?) {
            if (pin == null) {
                store.edit { it.remove(Keys.PIN_HASH) }
                return
            }
            require(PinHasher.isValidPin(pin)) { "PIN deve ter de 4 a 8 dígitos" }
            val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
            put(Keys.PIN_HASH, hash)
        }

        override suspend fun verifyPin(pin: String): Boolean {
            val stored = store.data.first()[Keys.PIN_HASH] ?: return false
            return withContext(Dispatchers.Default) { PinHasher.verify(pin, stored) }
        }

        override suspend fun clearAll() {
            store.edit { it.clear() }
        }

        private suspend fun <T> put(
            key: Preferences.Key<T>,
            value: T,
        ) {
            store.edit { it[key] = value }
        }

        private fun toSettings(p: Preferences): AppSettings {
            val d = AppSettings()
            return AppSettings(
                orientation = enumOr(p[Keys.ORIENTATION], d.orientation),
                maxVolume = p[Keys.MAX_VOLUME] ?: d.maxVolume,
                instrumentId = p[Keys.INSTRUMENT] ?: d.instrumentId,
                scale = enumOr(p[Keys.SCALE], d.scale),
                key = enumOr(p[Keys.KEY], d.key),
                animationLevel = enumOr(p[Keys.ANIMATION], d.animationLevel),
                vibration = p[Keys.VIBRATION] ?: d.vibration,
                usageReminderMinutes = p[Keys.REMINDER] ?: d.usageReminderMinutes,
                lockModeEnabled = p[Keys.LOCK_MODE] ?: d.lockModeEnabled,
                babyActivity = enumOr(p[Keys.BABY_ACTIVITY], d.babyActivity),
                animalSoundMode = enumOr(p[Keys.ANIMAL_SOUND], d.animalSoundMode),
                keyboardKeys = p[Keys.KEYBOARD_KEYS] ?: d.keyboardKeys,
                reverb = p[Keys.REVERB] ?: d.reverb,
                hasPin = p[Keys.PIN_HASH] != null,
                onboardingDone = p[Keys.ONBOARDING] ?: d.onboardingDone,
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(
            name: String?,
            default: E,
        ): E = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

        private object Keys {
            val ORIENTATION = stringPreferencesKey("orientacao")
            val MAX_VOLUME = floatPreferencesKey("volume_maximo")
            val INSTRUMENT = intPreferencesKey("instrumento")
            val SCALE = stringPreferencesKey("escala")
            val KEY = stringPreferencesKey("tonalidade")
            val ANIMATION = stringPreferencesKey("animacoes")
            val VIBRATION = booleanPreferencesKey("vibracao")
            val REMINDER = intPreferencesKey("aviso_tempo_min")
            val LOCK_MODE = booleanPreferencesKey("modo_travado")
            val BABY_ACTIVITY = stringPreferencesKey("brincadeira")
            val ANIMAL_SOUND = stringPreferencesKey("som_bichinhos")
            val KEYBOARD_KEYS = intPreferencesKey("teclas")
            val REVERB = floatPreferencesKey("ambiente")
            val PIN_HASH = stringPreferencesKey("pin_hash")
            val ONBOARDING = booleanPreferencesKey("boas_vindas")
        }
    }
