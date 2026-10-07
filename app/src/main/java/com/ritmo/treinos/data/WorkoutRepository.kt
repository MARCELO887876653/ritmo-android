package com.ritmo.treinos.data

import androidx.room.withTransaction

class WorkoutRepository(val db: RitmoDatabase) {
    val dao = db.dao()
    val exercises = dao.observeExercises()
    val templates = dao.observeTemplates()
    val workouts = dao.observeWorkouts()

    suspend fun saveExercise(id: Long?, name: String, notes: String): Long = db.withTransaction {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank() && clean.length <= 100) { "Use um nome entre 1 e 100 caracteres." }
        require(notes.length <= 2000) { "A observação pode ter até 2.000 caracteres." }
        val existing = dao.byName(normalizeName(clean))
        if (id == null && existing != null) {
            if (existing.archived) dao.updateExercise(existing.copy(archived = false))
            return@withTransaction existing.id
        }
        require(existing == null || existing.id == id) { "Já existe um exercício com esse nome." }
        if (id == null) dao.insertExercise(Exercise(name = clean, notes = notes))
        else { val current = requireNotNull(dao.exercise(id)); dao.updateExercise(current.copy(name = clean, normalizedName = normalizeName(clean), notes = notes)); id }
    }
    suspend fun archiveExercise(id: Long) { dao.exercise(id)?.let { dao.updateExercise(it.copy(archived = true)) } }
    suspend fun deleteExercise(id: Long) = db.withTransaction {
        val exercise = dao.exercise(id) ?: return@withTransaction
        dao.updateExercise(exercise.copy(archived = true))
        // Remove only future template membership; recorded and active sessions keep their data.
        dao.links().filter { it.exerciseId == id }.map { it.templateId }.distinct().forEach { templateId ->
            val remaining = dao.templateLinks(templateId).filter { it.exerciseId != id }
            dao.clearLinks(templateId)
            remaining.forEachIndexed { position, link -> dao.insertLink(link.copy(position = position)) }
        }
    }
    suspend fun saveTemplate(id: Long?, name: String, exercises: List<Long>): Long = db.withTransaction {
        require(name.trim().isNotEmpty() && name.length <= 100) { "Dê um nome ao treino (até 100 caracteres)." }
        require(exercises.isNotEmpty()) { "Adicione pelo menos um exercício." }
        require(exercises.distinct().size == exercises.size) { "O exercício já está no treino." }
        exercises.forEach { requireNotNull(dao.exercise(it)) }
        val templateId = id ?: dao.insertTemplate(WorkoutTemplate(name = name.trim()))
        if (id != null) { dao.updateTemplate(WorkoutTemplate(id, name.trim())); dao.clearLinks(id) }
        exercises.forEachIndexed { i, exerciseId -> dao.insertLink(TemplateExercise(templateId, exerciseId, i)) }
        templateId
    }
    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)
    suspend fun start(templateId: Long): Long = db.withTransaction {
        dao.active()?.let { return@withTransaction it.id }
        val template = requireNotNull(dao.template(templateId)) { "Treino não encontrado." }
        val links = dao.templateLinks(templateId)
        require(links.isNotEmpty()) { "Este treino não tem exercícios." }
        val id = dao.insertWorkout(WorkoutSession(templateId = templateId, name = template.name, startedAt = System.currentTimeMillis()))
        links.forEach { link ->
            val exercise = requireNotNull(dao.exercise(link.exerciseId))
            val es = dao.insertExerciseSession(ExerciseSession(workoutSessionId = id, exerciseId = exercise.id, name = exercise.name, position = link.position))
            dao.insertSet(ExerciseSet(exerciseSessionId = es, number = 1))
        }
        id
    }
    private suspend fun editable(id: Long): ExerciseSession {
        val session = requireNotNull(dao.exerciseSession(id))
        require(dao.workout(session.workoutSessionId)?.endedAt == null) { "O treino já foi finalizado." }
        return session
    }
    suspend fun addSet(id: Long) = db.withTransaction {
        editable(id); val sets = dao.sessionSets(id)
        dao.insertSet(ExerciseSet(exerciseSessionId = id, number = (sets.maxOfOrNull { it.number } ?: 0) + 1))
    }
    suspend fun removeSet(id: Long) = db.withTransaction {
        val set = requireNotNull(dao.set(id)); editable(set.exerciseSessionId); dao.deleteSet(id)
        dao.sessionSets(set.exerciseSessionId).forEachIndexed { i, item -> dao.updateSet(item.copy(number = i + 1)) }
    }
    suspend fun saveSet(id: Long, weight: Double, reps: Int, completed: Boolean) = db.withTransaction {
        require(weight.isFinite() && weight in 0.0..10000.0 && reps in 0..10000) { "Informe carga e repetições válidas." }
        require(!completed || reps > 0) { "Informe as repetições antes de concluir a série." }
        val current = requireNotNull(dao.set(id)); editable(current.exerciseSessionId)
        dao.updateSet(current.copy(weight = weight, reps = reps, completed = completed))
    }
    suspend fun note(id: Long, text: String) = db.withTransaction {
        require(text.length <= 2000) { "A observação pode ter até 2.000 caracteres." }
        dao.updateExerciseSession(editable(id).copy(notes = text))
    }
    suspend fun copyPrevious(id: Long) = db.withTransaction {
        val session = editable(id)
        val previous = dao.previous(session.exerciseId, session.workoutSessionId)?.sets?.filter { it.completed }?.sortedBy { it.number }.orEmpty()
        require(previous.isNotEmpty()) { "Ainda não há séries concluídas anteriores." }
        val current = dao.sessionSets(id)
        require(current.none { it.completed || it.reps > 0 || it.weight > 0 }) { "Copie antes de preencher as séries atuais." }
        dao.clearSets(id)
        previous.forEachIndexed { i, set -> dao.insertSet(set.copy(id = 0, exerciseSessionId = id, number = i + 1, completed = false)) }
    }
    suspend fun finish(id: Long) = db.withTransaction {
        val workout = requireNotNull(dao.workout(id))
        require(dao.exerciseSessions().filter { it.workoutSessionId == id }.any { es -> dao.sessionSets(es.id).any { it.completed } }) { "Conclua pelo menos uma série para finalizar." }
        if (workout.endedAt == null) dao.updateWorkout(workout.copy(endedAt = System.currentTimeMillis()))
    }
}
