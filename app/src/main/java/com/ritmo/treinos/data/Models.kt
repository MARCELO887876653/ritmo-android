package com.ritmo.treinos.data

import androidx.room.*
import java.util.Locale

fun normalizeName(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

@Entity(tableName = "exercises", indices = [Index(value = ["normalizedName"], unique = true)])
data class Exercise(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val normalizedName: String = normalizeName(name), val notes: String = "", @ColumnInfo(defaultValue = "0") val archived: Boolean = false)
@Entity(tableName = "templates")
data class WorkoutTemplate(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "template_exercises", primaryKeys = ["templateId", "exerciseId"],
    foreignKeys = [ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("exerciseId")])
data class TemplateExercise(val templateId: Long, val exerciseId: Long, val position: Int)
@Entity(tableName = "workout_sessions", foreignKeys = [ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("templateId")])
data class WorkoutSession(@PrimaryKey(autoGenerate = true) val id: Long = 0, val templateId: Long?, val name: String, val startedAt: Long, val endedAt: Long? = null)
@Entity(tableName = "exercise_sessions", foreignKeys = [ForeignKey(entity = WorkoutSession::class, parentColumns = ["id"], childColumns = ["workoutSessionId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("workoutSessionId"), Index("exerciseId"), Index(value = ["workoutSessionId", "exerciseId"], unique = true)])
data class ExerciseSession(@PrimaryKey(autoGenerate = true) val id: Long = 0, val workoutSessionId: Long, val exerciseId: Long, val name: String, val position: Int, val notes: String = "")
@Entity(tableName = "exercise_sets", foreignKeys = [ForeignKey(entity = ExerciseSession::class, parentColumns = ["id"], childColumns = ["exerciseSessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("exerciseSessionId")])
data class ExerciseSet(@PrimaryKey(autoGenerate = true) val id: Long = 0, val exerciseSessionId: Long, val number: Int, val weight: Double = 0.0, val reps: Int = 0, val completed: Boolean = false)

data class TemplateDetail(@Embedded val template: WorkoutTemplate, @Relation(parentColumn = "id", entityColumn = "templateId") val links: List<TemplateExercise>)
data class ExerciseDetail(@Embedded val session: ExerciseSession, @Relation(parentColumn = "id", entityColumn = "exerciseSessionId") val sets: List<ExerciseSet>)
data class WorkoutDetail(@Embedded val session: WorkoutSession, @Relation(entity = ExerciseSession::class, parentColumn = "id", entityColumn = "workoutSessionId") val exercises: List<ExerciseDetail>) {
    val completedSets get() = exercises.sumOf { it.sets.count(ExerciseSet::completed) }
}
