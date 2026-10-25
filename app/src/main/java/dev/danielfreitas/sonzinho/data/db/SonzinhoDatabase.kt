package dev.danielfreitas.sonzinho.data.db

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "musicas")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tempoBpm: Int,
    val steps: Int,
    val scale: String,
    val musicKey: String,
    /** Linhas e notas em JSON (`List<Track>`). */
    val tracksJson: String,
    val showInBaby: Boolean,
    val colorIndex: Int,
    val updatedAt: Long,
)

@Entity(tableName = "sons")
data class SoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** `SoundParams` em JSON. */
    val paramsJson: String,
    val colorIndex: Int,
    val showInBaby: Boolean,
)

@Entity(tableName = "gravacoes")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMs: Long,
    /** `List<RecordedNote>` em JSON. */
    val notesJson: String,
    val showInBaby: Boolean,
    val createdAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM musicas ORDER BY updatedAt DESC")
    fun songs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM sons ORDER BY id DESC")
    fun sounds(): Flow<List<SoundEntity>>

    @Query("SELECT * FROM gravacoes ORDER BY createdAt DESC")
    fun recordings(): Flow<List<RecordingEntity>>

    @Upsert
    suspend fun upsertSong(song: SongEntity): Long

    @Upsert
    suspend fun upsertSound(sound: SoundEntity): Long

    @Upsert
    suspend fun upsertRecording(recording: RecordingEntity): Long

    @Query("DELETE FROM musicas WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM sons WHERE id = :id")
    suspend fun deleteSound(id: Long)

    @Query("DELETE FROM gravacoes WHERE id = :id")
    suspend fun deleteRecording(id: Long)

    @Query("UPDATE musicas SET name = :name WHERE id = :id")
    suspend fun renameSong(
        id: Long,
        name: String,
    )

    @Query("UPDATE sons SET name = :name WHERE id = :id")
    suspend fun renameSound(
        id: Long,
        name: String,
    )

    @Query("UPDATE gravacoes SET name = :name WHERE id = :id")
    suspend fun renameRecording(
        id: Long,
        name: String,
    )

    @Query("UPDATE musicas SET showInBaby = :visible WHERE id = :id")
    suspend fun setSongVisible(
        id: Long,
        visible: Boolean,
    )

    @Query("UPDATE sons SET showInBaby = :visible WHERE id = :id")
    suspend fun setSoundVisible(
        id: Long,
        visible: Boolean,
    )

    @Query("UPDATE gravacoes SET showInBaby = :visible WHERE id = :id")
    suspend fun setRecordingVisible(
        id: Long,
        visible: Boolean,
    )

    @Query("DELETE FROM musicas")
    suspend fun clearSongs()

    @Query("DELETE FROM sons")
    suspend fun clearSounds()

    @Query("DELETE FROM gravacoes")
    suspend fun clearRecordings()
}

@Database(entities = [SongEntity::class, SoundEntity::class, RecordingEntity::class], version = 1, exportSchema = true)
abstract class SonzinhoDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): SonzinhoDatabase =
            Room
                .databaseBuilder(context, SonzinhoDatabase::class.java, "sonzinho.db")
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}
