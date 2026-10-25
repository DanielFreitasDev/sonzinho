package dev.danielfreitas.sonzinho.domain.repository

import dev.danielfreitas.sonzinho.domain.model.AnimalSoundMode
import dev.danielfreitas.sonzinho.domain.model.AnimationLevel
import dev.danielfreitas.sonzinho.domain.model.AppSettings
import dev.danielfreitas.sonzinho.domain.model.BabyActivity
import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.ScreenOrientation
import dev.danielfreitas.sonzinho.domain.model.Song
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOrientation(value: ScreenOrientation)

    suspend fun setMaxVolume(value: Float)

    suspend fun setInstrument(instrumentId: Int)

    suspend fun setScale(value: Scale)

    suspend fun setKey(value: MusicKey)

    suspend fun setAnimationLevel(value: AnimationLevel)

    suspend fun setVibration(enabled: Boolean)

    suspend fun setUsageReminder(minutes: Int)

    suspend fun setLockMode(enabled: Boolean)

    suspend fun setBabyActivity(value: BabyActivity)

    suspend fun setAnimalSoundMode(value: AnimalSoundMode)

    suspend fun setKeyboardKeys(count: Int)

    suspend fun setReverb(value: Float)

    suspend fun setOnboardingDone(done: Boolean)

    /** Grava o PIN (somente o hash com sal). `null` remove o PIN e volta ao desafio de conta. */
    suspend fun setPin(pin: String?)

    suspend fun verifyPin(pin: String): Boolean

    suspend fun clearAll()
}

interface LibraryRepository {
    val songs: Flow<List<Song>>
    val sounds: Flow<List<CustomSound>>
    val recordings: Flow<List<Recording>>

    suspend fun saveSong(song: Song): Long

    suspend fun deleteSong(id: Long)

    suspend fun saveSound(sound: CustomSound): Long

    suspend fun deleteSound(id: Long)

    suspend fun saveRecording(recording: Recording): Long

    suspend fun deleteRecording(id: Long)

    suspend fun renameSong(
        id: Long,
        name: String,
    )

    suspend fun renameSound(
        id: Long,
        name: String,
    )

    suspend fun renameRecording(
        id: Long,
        name: String,
    )

    suspend fun setSongVisible(
        id: Long,
        visible: Boolean,
    )

    suspend fun setSoundVisible(
        id: Long,
        visible: Boolean,
    )

    suspend fun setRecordingVisible(
        id: Long,
        visible: Boolean,
    )

    suspend fun clearAll()
}
