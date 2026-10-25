package dev.danielfreitas.sonzinho.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.danielfreitas.sonzinho.data.audio.NativeAudioEngine
import dev.danielfreitas.sonzinho.data.db.RoomLibraryRepository
import dev.danielfreitas.sonzinho.data.db.SonzinhoDatabase
import dev.danielfreitas.sonzinho.data.settings.DataStoreSettingsRepository
import dev.danielfreitas.sonzinho.domain.audio.AudioEngine
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import dev.danielfreitas.sonzinho.domain.repository.SettingsRepository
import dev.danielfreitas.sonzinho.domain.usecase.FreePlayRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "configuracoes")

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun audioEngine(impl: NativeAudioEngine): AudioEngine

    @Binds
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun library(impl: RoomLibraryRepository): LibraryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun dataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsStore

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): SonzinhoDatabase = SonzinhoDatabase.create(context)

    @Provides
    @Singleton
    fun recorder(): FreePlayRecorder = FreePlayRecorder { SystemClock.elapsedRealtime() }
}
