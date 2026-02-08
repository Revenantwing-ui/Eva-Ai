package com.aiautomation.assistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Entity for storing action sequences
 */
@Entity(tableName = "action_sequences")
data class ActionSequence(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val actionType: String,  // CLICK, SWIPE, LONG_PRESS, SCROLL, TYPE_TEXT, WAIT
    val x: Float? = null,
    val y: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val direction: String? = null,  // UP, DOWN, LEFT, RIGHT
    val text: String? = null,
    val duration: Long? = null,
    
    val sequenceId: String,  // Groups related actions together
    val orderInSequence: Int,
    
    val timestamp: Long = System.currentTimeMillis(),
    val appPackageName: String? = null,
    val screenContext: String? = null,  // JSON describing screen state
    
    val confidence: Float = 1.0f,
    val successRate: Float = 1.0f,
    val executionCount: Int = 0
)

/**
 * Entity for storing recognized patterns
 */
@Entity(tableName = "recognized_patterns")
data class RecognizedPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val patternType: String,  // BUTTON, TEXT_FIELD, IMAGE, ICON, etc.
    val confidence: Float,
    
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    
    val features: String? = null,  // JSON array of feature vector
    val visualHash: String? = null,  // Hash of visual appearance
    
    val timestamp: Long = System.currentTimeMillis(),
    val appPackageName: String? = null,
    val screenId: String? = null
)

/**
 * Entity for storing learned tasks/workflows
 */
@Entity(tableName = "learned_tasks")
data class LearnedTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val taskName: String,
    val description: String? = null,
    
    val sequenceId: String,  // Links to ActionSequence
    val triggerPattern: String? = null,  // Pattern that triggers this task
    
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long? = null,
    val useCount: Int = 0,
    
    val isEnabled: Boolean = true,
    val priority: Int = 0
)

/**
 * DAO for ActionSequence
 */
@Dao
interface ActionSequenceDao {
    
    @Insert
    suspend fun insertActionSequence(action: ActionSequence): Long
    
    @Insert
    suspend fun insertActionSequences(actions: List<ActionSequence>)
    
    @Update
    suspend fun updateActionSequence(action: ActionSequence)
    
    @Delete
    suspend fun deleteActionSequence(action: ActionSequence)
    
    @Query("SELECT * FROM action_sequences WHERE sequenceId = :sequenceId ORDER BY orderInSequence ASC")
    suspend fun getActionsBySequence(sequenceId: String): List<ActionSequence>
    
    @Query("SELECT * FROM action_sequences WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecentActions(since: Long): List<ActionSequence>
    
    @Query("SELECT DISTINCT sequenceId FROM action_sequences ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSequenceIds(limit: Int = 10): List<String>
    
    @Query("DELETE FROM action_sequences WHERE sequenceId = :sequenceId")
    suspend fun deleteSequence(sequenceId: String)
    
    @Query("SELECT * FROM action_sequences ORDER BY timestamp DESC")
    fun getAllActionsFlow(): Flow<List<ActionSequence>>
}

/**
 * DAO for RecognizedPattern
 */
@Dao
interface RecognizedPatternDao {
    
    @Insert
    suspend fun insertPattern(pattern: RecognizedPattern): Long
    
    @Insert
    suspend fun insertPatterns(patterns: List<RecognizedPattern>)
    
    @Update
    suspend fun updatePattern(pattern: RecognizedPattern)
    
    @Delete
    suspend fun deletePattern(pattern: RecognizedPattern)
    
    @Query("SELECT * FROM recognized_patterns WHERE patternType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getPatternsByType(type: String, limit: Int = 100): List<RecognizedPattern>
    
    @Query("SELECT * FROM recognized_patterns WHERE appPackageName = :packageName ORDER BY timestamp DESC")
    suspend fun getPatternsByApp(packageName: String): List<RecognizedPattern>
    
    @Query("SELECT * FROM recognized_patterns WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecentPatterns(since: Long): List<RecognizedPattern>
    
    @Query("DELETE FROM recognized_patterns WHERE timestamp < :before")
    suspend fun deleteOldPatterns(before: Long)
    
    @Query("SELECT * FROM recognized_patterns ORDER BY timestamp DESC")
    fun getAllPatternsFlow(): Flow<List<RecognizedPattern>>
}

/**
 * DAO for LearnedTask
 */
@Dao
interface LearnedTaskDao {
    
    @Insert
    suspend fun insertTask(task: LearnedTask): Long
    
    @Update
    suspend fun updateTask(task: LearnedTask)
    
    @Delete
    suspend fun deleteTask(task: LearnedTask)
    
    @Query("SELECT * FROM learned_tasks WHERE isEnabled = 1 ORDER BY priority DESC, useCount DESC")
    suspend fun getEnabledTasks(): List<LearnedTask>
    
    @Query("SELECT * FROM learned_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): LearnedTask?
    
    @Query("SELECT * FROM learned_tasks WHERE sequenceId = :sequenceId")
    suspend fun getTaskBySequence(sequenceId: String): LearnedTask?
    
    @Query("UPDATE learned_tasks SET lastUsed = :timestamp, useCount = useCount + 1 WHERE id = :taskId")
    suspend fun incrementTaskUsage(taskId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM learned_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<LearnedTask>>
}

/**
 * Main database
 */
@Database(
    entities = [
        ActionSequence::class,
        RecognizedPattern::class,
        LearnedTask::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun actionSequenceDao(): ActionSequenceDao
    abstract fun recognizedPatternDao(): RecognizedPatternDao
    abstract fun learnedTaskDao(): LearnedTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "automation_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
