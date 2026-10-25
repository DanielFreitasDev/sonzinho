package dev.danielfreitas.sonzinho.data.db

import dev.danielfreitas.sonzinho.domain.model.CustomSound
import dev.danielfreitas.sonzinho.domain.model.MusicKey
import dev.danielfreitas.sonzinho.domain.model.RecordedNote
import dev.danielfreitas.sonzinho.domain.model.Recording
import dev.danielfreitas.sonzinho.domain.model.Scale
import dev.danielfreitas.sonzinho.domain.model.Song
import dev.danielfreitas.sonzinho.domain.model.SoundParams
import dev.danielfreitas.sonzinho.domain.model.Track
import dev.danielfreitas.sonzinho.domain.model.TrackKind
import dev.danielfreitas.sonzinho.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class RoomLibraryRepository
    @Inject
    constructor(
        database: SonzinhoDatabase,
    ) : LibraryRepository {
        private val dao = database.libraryDao()

        override val songs: Flow<List<Song>> = dao.songs().map { list -> list.map(LibraryMapper::toSong) }
        override val sounds: Flow<List<CustomSound>> = dao.sounds().map { list -> list.map(LibraryMapper::toSound) }
        override val recordings: Flow<List<Recording>> =
            dao.recordings().map { list -> list.map(LibraryMapper::toRecording) }

        override suspend fun saveSong(song: Song): Long {
            val id = dao.upsertSong(LibraryMapper.fromSong(song.copy(updatedAt = System.currentTimeMillis())))
            return if (song.id != 0L) song.id else id
        }

        override suspend fun deleteSong(id: Long) = dao.deleteSong(id)

        override suspend fun saveSound(sound: CustomSound): Long {
            val id = dao.upsertSound(LibraryMapper.fromSound(sound))
            return if (sound.id != 0L) sound.id else id
        }

        override suspend fun deleteSound(id: Long) = dao.deleteSound(id)

        override suspend fun saveRecording(recording: Recording): Long {
            val id = dao.upsertRecording(LibraryMapper.fromRecording(recording))
            return if (recording.id != 0L) recording.id else id
        }

        override suspend fun deleteRecording(id: Long) = dao.deleteRecording(id)

        override suspend fun renameSong(
            id: Long,
            name: String,
        ) = dao.renameSong(id, name.trim().take(MAX_NAME))

        override suspend fun renameSound(
            id: Long,
            name: String,
        ) = dao.renameSound(id, name.trim().take(MAX_NAME))

        override suspend fun renameRecording(
            id: Long,
            name: String,
        ) = dao.renameRecording(id, name.trim().take(MAX_NAME))

        override suspend fun setSongVisible(
            id: Long,
            visible: Boolean,
        ) = dao.setSongVisible(id, visible)

        override suspend fun setSoundVisible(
            id: Long,
            visible: Boolean,
        ) = dao.setSoundVisible(id, visible)

        override suspend fun setRecordingVisible(
            id: Long,
            visible: Boolean,
        ) = dao.setRecordingVisible(id, visible)

        override suspend fun clearAll() {
            dao.clearSongs()
            dao.clearSounds()
            dao.clearRecordings()
        }

        private companion object {
            const val MAX_NAME = 40
        }
    }

/** Conversões entre entidades do banco e modelos do domínio (JSON para listas aninhadas). */
internal object LibraryMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val tracksSerializer = ListSerializer(Track.serializer())
    private val notesSerializer = ListSerializer(RecordedNote.serializer())

    fun toSong(e: SongEntity): Song {
        val stored = runCatching { json.decodeFromString(tracksSerializer, e.tracksJson) }.getOrDefault(emptyList())
        // Garante as quatro linhas mesmo em dados antigos ou corrompidos.
        val tracks = TrackKind.entries.map { kind -> stored.firstOrNull { it.kind == kind } ?: Track(kind) }
        return Song(
            id = e.id,
            name = e.name,
            tempoBpm = e.tempoBpm.coerceIn(Song.tempoRange),
            steps = if (e.steps in Song.stepOptions) e.steps else 16,
            scale = Scale.entries.firstOrNull { it.name == e.scale } ?: Scale.PENTATONIC_MAJOR,
            key = MusicKey.entries.firstOrNull { it.name == e.musicKey } ?: MusicKey.C,
            tracks = tracks,
            showInBaby = e.showInBaby,
            colorIndex = e.colorIndex,
            updatedAt = e.updatedAt,
        )
    }

    fun fromSong(s: Song): SongEntity =
        SongEntity(
            id = s.id,
            name = s.name,
            tempoBpm = s.tempoBpm,
            steps = s.steps,
            scale = s.scale.name,
            musicKey = s.key.name,
            tracksJson = json.encodeToString(tracksSerializer, s.tracks),
            showInBaby = s.showInBaby,
            colorIndex = s.colorIndex,
            updatedAt = s.updatedAt,
        )

    fun toSound(e: SoundEntity): CustomSound =
        CustomSound(
            id = e.id,
            name = e.name,
            params = runCatching { json.decodeFromString(SoundParams.serializer(), e.paramsJson) }.getOrDefault(SoundParams()),
            colorIndex = e.colorIndex,
            showInBaby = e.showInBaby,
        )

    fun fromSound(s: CustomSound): SoundEntity =
        SoundEntity(
            id = s.id,
            name = s.name,
            paramsJson = json.encodeToString(SoundParams.serializer(), s.params),
            colorIndex = s.colorIndex,
            showInBaby = s.showInBaby,
        )

    fun toRecording(e: RecordingEntity): Recording =
        Recording(
            id = e.id,
            name = e.name,
            durationMs = e.durationMs,
            notes = runCatching { json.decodeFromString(notesSerializer, e.notesJson) }.getOrDefault(emptyList()),
            showInBaby = e.showInBaby,
            createdAt = e.createdAt,
        )

    fun fromRecording(r: Recording): RecordingEntity =
        RecordingEntity(
            id = r.id,
            name = r.name,
            durationMs = r.durationMs,
            notesJson = json.encodeToString(notesSerializer, r.notes),
            showInBaby = r.showInBaby,
            createdAt = if (r.createdAt == 0L) System.currentTimeMillis() else r.createdAt,
        )
}
