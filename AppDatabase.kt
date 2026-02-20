package com.terminal.universe.data.local

import androidx.room.*
import com.terminal.universe.domain.model.Command
import com.terminal.universe.domain.model.TerminalSession
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        CommandEntity::class,
        SessionEntity::class,
        CommandHistoryEntity::class,
        AchievementEntity::class,
        UserProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun commandDao(): CommandDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): CommandHistoryDao
    abstract fun achievementDao(): AchievementDao
    abstract fun userProgressDao(): UserProgressDao
}

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey
    val name: String,
    val description: String,
    val category: String,
    val syntax: String,
    val example: String,
    val safetyLevel: String,
    val requiresPackage: String?
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val workingDirectory: String,
    val createdAt: Long,
    val lastUsed: Long,
    val isSplitMode: Boolean = false
)

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val command: String,
    val timestamp: Long,
    val exitCode: Int,
    val workingDirectory: String
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val xpReward: Int,
    val unlockedAt: Long? = null,
    val icon: String
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey
    val id: String = "user",
    val experience: Long,
    val level: String,
    val commandsExecuted: Int,
    val sessionsCreated: Int,
    val uptimeSeconds: Long
)

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands")
    fun getAllCommands(): Flow<List<CommandEntity>>
    
    @Query("SELECT * FROM commands WHERE name LIKE :query || '%'")
    fun searchCommands(query: String): List<CommandEntity>
    
    @Query("SELECT * FROM commands WHERE name = :name")
    fun getCommand(name: String): CommandEntity?
    
    @Insert
    suspend fun insertCommand(command: CommandEntity)
    
    @Insert
    suspend fun insertAll(commands: List<CommandEntity>)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastUsed DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getSession(id: String): Flow<SessionEntity?>
    
    @Insert
    suspend fun insertSession(session: SessionEntity)
    
    @Update
    suspend fun updateSession(session: SessionEntity)
    
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCommands(sessionId: String, limit: Int = 100): Flow<List<CommandHistoryEntity>>
    
    @Insert
    suspend fun insertCommand(history: CommandHistoryEntity)
    
    @Query("DELETE FROM command_history WHERE timestamp < :cutoff")
    suspend fun deleteOldCommands(cutoff: Long)
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
