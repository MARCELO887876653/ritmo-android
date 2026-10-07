package com.ritmo.treinos.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface RitmoDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE") fun observeExercises(): Flow<List<Exercise>>
    @Transaction @Query("SELECT * FROM templates ORDER BY id") fun observeTemplates(): Flow<List<TemplateDetail>>
    @Transaction @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC, id DESC") fun observeWorkouts(): Flow<List<WorkoutDetail>>
    @Query("SELECT * FROM exercises ORDER BY id") suspend fun exercises(): List<Exercise>
    @Query("SELECT * FROM templates ORDER BY id") suspend fun templates(): List<WorkoutTemplate>
    @Query("SELECT * FROM template_exercises ORDER BY templateId, position") suspend fun links(): List<TemplateExercise>
    @Query("SELECT * FROM workout_sessions ORDER BY id") suspend fun workouts(): List<WorkoutSession>
    @Query("SELECT * FROM exercise_sessions ORDER BY id") suspend fun exerciseSessions(): List<ExerciseSession>
    @Query("SELECT * FROM exercise_sets ORDER BY id") suspend fun sets(): List<ExerciseSet>
    @Query("SELECT * FROM exercises WHERE normalizedName=:name LIMIT 1") suspend fun byName(name: String): Exercise?
    @Query("SELECT * FROM exercises WHERE id=:id") suspend fun exercise(id: Long): Exercise?
    @Query("SELECT * FROM templates WHERE id=:id") suspend fun template(id: Long): WorkoutTemplate?
    @Query("SELECT * FROM template_exercises WHERE templateId=:id ORDER BY position") suspend fun templateLinks(id: Long): List<TemplateExercise>
    @Query("SELECT * FROM workout_sessions WHERE endedAt IS NULL LIMIT 1") suspend fun active(): WorkoutSession?
    @Query("SELECT * FROM workout_sessions WHERE id=:id") suspend fun workout(id: Long): WorkoutSession?
    @Query("SELECT * FROM exercise_sessions WHERE id=:id") suspend fun exerciseSession(id: Long): ExerciseSession?
    @Query("SELECT * FROM exercise_sets WHERE id=:id") suspend fun set(id: Long): ExerciseSet?
    @Query("SELECT * FROM exercise_sets WHERE exerciseSessionId=:id ORDER BY number, id") suspend fun sessionSets(id: Long): List<ExerciseSet>
    @Transaction @Query("SELECT es.* FROM exercise_sessions es JOIN workout_sessions ws ON ws.id=es.workoutSessionId WHERE es.exerciseId=:exerciseId AND ws.endedAt IS NOT NULL AND ws.id!=:currentWorkoutId ORDER BY ws.startedAt DESC, ws.id DESC LIMIT 1") suspend fun previous(exerciseId: Long, currentWorkoutId: Long): ExerciseDetail?
    @Insert suspend fun insertExercise(value: Exercise): Long
    @Insert suspend fun insertTemplate(value: WorkoutTemplate): Long
    @Insert suspend fun insertLink(value: TemplateExercise)
    @Insert suspend fun insertWorkout(value: WorkoutSession): Long
    @Insert suspend fun insertExerciseSession(value: ExerciseSession): Long
    @Insert suspend fun insertSet(value: ExerciseSet): Long
    @Update suspend fun updateExercise(value: Exercise)
    @Update suspend fun updateTemplate(value: WorkoutTemplate)
    @Update suspend fun updateWorkout(value: WorkoutSession)
    @Update suspend fun updateExerciseSession(value: ExerciseSession)
    @Update suspend fun updateSet(value: ExerciseSet)
    @Query("DELETE FROM template_exercises WHERE templateId=:id") suspend fun clearLinks(id: Long)
    @Query("DELETE FROM templates WHERE id=:id") suspend fun deleteTemplate(id: Long)
    @Query("DELETE FROM exercise_sets WHERE id=:id") suspend fun deleteSet(id: Long)
    @Query("DELETE FROM exercise_sets WHERE exerciseSessionId=:id") suspend fun clearSets(id: Long)
    @Query("DELETE FROM workout_sessions WHERE id=:id AND endedAt IS NOT NULL") suspend fun deleteFinishedWorkout(id: Long): Int
    @Query("DELETE FROM exercise_sessions WHERE id=:id AND workoutSessionId IN (SELECT id FROM workout_sessions WHERE endedAt IS NOT NULL)") suspend fun deleteFinishedExerciseSession(id: Long): Int
    @Query("DELETE FROM workout_sessions") suspend fun clearWorkouts()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM exercises") suspend fun clearExercises()
}

@Database(entities = [Exercise::class, WorkoutTemplate::class, TemplateExercise::class, WorkoutSession::class, ExerciseSession::class, ExerciseSet::class], version = 2, exportSchema = true)
abstract class RitmoDatabase : RoomDatabase() {
    abstract fun dao(): RitmoDao
    companion object {
        // V1 had the same data model, before archiving was introduced. Never reset user data.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE exercises ADD COLUMN archived INTEGER NOT NULL DEFAULT 0") }
        }
        fun open(context: Context, name: String = "ritmo.db") = Room.databaseBuilder(context, RitmoDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
    }
}
