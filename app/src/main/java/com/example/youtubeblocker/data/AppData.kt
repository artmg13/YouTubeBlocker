package com.example.youtubeblocker.data

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import com.example.youtubeblocker.ReasonRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "entries", indices = [Index(value = ["confirmedAt", "id"])])
data class Entry(
    @PrimaryKey val id: String,
    val confirmedAt: Long,
    val zoneId: String,
    val reason: String,
    val requiredLength: Int,
)

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY confirmedAt DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<Entry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: Entry)

    @Query("DELETE FROM entries")
    suspend fun clear()
}

@Database(entities = [Entry::class], version = 1, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

class AppData(context: Context) {
    val entries = Room.databaseBuilder(context, JournalDatabase::class.java, "journal.db")
        .build().entries()
    private val store = context.settingsStore
    private val minimumKey = intPreferencesKey("minimum_length")
    val minimum: Flow<Int> = store.data.map {
        (it[minimumKey] ?: ReasonRules.DEFAULT_MINIMUM).coerceIn(ReasonRules.MINIMUM, ReasonRules.MAXIMUM)
    }

    suspend fun setMinimum(value: Int) {
        require(value in ReasonRules.MINIMUM..ReasonRules.MAXIMUM)
        store.edit { it[minimumKey] = value }
    }
}

class BlockerApplication : Application() {
    val data by lazy { AppData(this) }
}
