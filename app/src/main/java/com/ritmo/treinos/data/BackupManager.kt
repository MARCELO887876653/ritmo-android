package com.ritmo.treinos.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Logical backup: no WAL files, no signature, no remote credentials. Restore is atomic. */
class BackupManager(private val context: Context, private val db: RitmoDatabase) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val json = snapshot()
        context.contentResolver.openOutputStream(uri, "wt").use { out -> requireNotNull(out).write(json.toByteArray(Charsets.UTF_8)) }
    }
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytesLimited() }
        restoreJson(bytes.toString(Charsets.UTF_8))
    }
    suspend fun snapshot(): String = db.withTransaction {
        val d = db.dao()
        JSONObject().put("format", "ritmo-backup").put("version", 1)
            .put("exercises", array(d.exercises()) { JSONObject().put("id", it.id).put("name", it.name).put("notes", it.notes).put("archived", it.archived) })
            .put("templates", array(d.templates()) { JSONObject().put("id", it.id).put("name", it.name) })
            .put("links", array(d.links()) { JSONObject().put("templateId", it.templateId).put("exerciseId", it.exerciseId).put("position", it.position) })
            .put("workouts", array(d.workouts()) { JSONObject().put("id", it.id).put("templateId", it.templateId ?: JSONObject.NULL).put("name", it.name).put("startedAt", it.startedAt).put("endedAt", it.endedAt ?: JSONObject.NULL) })
            .put("sessions", array(d.exerciseSessions()) { JSONObject().put("id", it.id).put("workoutSessionId", it.workoutSessionId).put("exerciseId", it.exerciseId).put("name", it.name).put("position", it.position).put("notes", it.notes) })
            .put("sets", array(d.sets()) { JSONObject().put("id", it.id).put("exerciseSessionId", it.exerciseSessionId).put("number", it.number).put("weight", it.weight).put("reps", it.reps).put("completed", it.completed) }).toString(2)
    }
    suspend fun restoreJson(raw: String) {
        require(raw.toByteArray().size <= 20 * 1024 * 1024) { "Backup excede 20 MB." }
        val j = JSONObject(raw)
        require(j.getString("format") == "ritmo-backup" && j.getInt("version") == 1) { "Formato de backup incompatível." }
        val exercises = rows(j, "exercises") { Exercise(it.getLong("id"), it.getString("name"), notes = it.getString("notes"), archived = it.getBoolean("archived")) }
        val templates = rows(j, "templates") { WorkoutTemplate(it.getLong("id"), it.getString("name")) }
        val links = rows(j, "links") { TemplateExercise(it.getLong("templateId"), it.getLong("exerciseId"), it.getInt("position")) }
        val workouts = rows(j, "workouts") { WorkoutSession(it.getLong("id"), it.nullableLong("templateId"), it.getString("name"), it.getLong("startedAt"), it.nullableLong("endedAt")) }
        val sessions = rows(j, "sessions") { ExerciseSession(it.getLong("id"), it.getLong("workoutSessionId"), it.getLong("exerciseId"), it.getString("name"), it.getInt("position"), it.getString("notes")) }
        val sets = rows(j, "sets") { ExerciseSet(it.getLong("id"), it.getLong("exerciseSessionId"), it.getInt("number"), it.getDouble("weight"), it.getInt("reps"), it.getBoolean("completed")) }
        fun ids(values: List<Long>): Set<Long> { require(values.all { it > 0 } && values.distinct().size == values.size) { "IDs inválidos no backup." }; return values.toSet() }
        val eIds = ids(exercises.map { it.id }); val tIds = ids(templates.map { it.id }); val wIds = ids(workouts.map { it.id }); val sIds = ids(sessions.map { it.id }); ids(sets.map { it.id })
        require(exercises.map { it.normalizedName }.distinct().size == exercises.size)
        require(exercises.all { it.name.isNotBlank() && it.name.length <= 100 && it.notes.length <= 2000 })
        require(templates.all { it.name.isNotBlank() && it.name.length <= 100 })
        require(links.all { it.templateId in tIds && it.exerciseId in eIds && it.position >= 0 })
        require(links.map { it.templateId to it.exerciseId }.distinct().size == links.size)
        require(links.map { it.templateId to it.position }.distinct().size == links.size)
        require(workouts.count { it.endedAt == null } <= 1)
        require(workouts.all { (it.templateId == null || it.templateId in tIds) && it.name.isNotBlank() && it.startedAt > 0 && (it.endedAt == null || it.endedAt >= it.startedAt) })
        require(sessions.all { it.workoutSessionId in wIds && it.exerciseId in eIds && it.position >= 0 && it.name.isNotBlank() && it.notes.length <= 2000 })
        require(sessions.map { it.workoutSessionId to it.exerciseId }.distinct().size == sessions.size)
        require(sessions.map { it.workoutSessionId to it.position }.distinct().size == sessions.size)
        require(sets.all { it.exerciseSessionId in sIds && it.number > 0 && it.weight.isFinite() && it.weight in 0.0..10000.0 && it.reps in 0..10000 && (!it.completed || it.reps > 0) })
        require(sets.map { it.exerciseSessionId to it.number }.distinct().size == sets.size)
        db.withTransaction {
            val d = db.dao(); d.clearWorkouts(); d.clearTemplates(); d.clearExercises()
            exercises.forEach { d.insertExercise(it) }; templates.forEach { d.insertTemplate(it) }; links.forEach { d.insertLink(it) }
            workouts.forEach { d.insertWorkout(it) }; sessions.forEach { d.insertExerciseSession(it) }; sets.forEach { d.insertSet(it) }
        }
    }
    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; require(output.size() + n <= 20 * 1024 * 1024) { "Backup excede 20 MB." }; output.write(buffer, 0, n) }
        return output.toByteArray()
    }
    private fun JSONObject.nullableLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun <T> rows(j: JSONObject, key: String, parse: (JSONObject) -> T): List<T> { val a = j.getJSONArray(key); require(a.length() <= 100000); return List(a.length()) { parse(a.getJSONObject(it)) } }
    private fun <T> array(values: List<T>, toJson: (T) -> JSONObject): JSONArray = JSONArray().apply { values.forEach { put(toJson(it)) } }
}
