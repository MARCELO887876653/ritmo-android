package com.ritmo.treinos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritmo.treinos.data.*

@Composable fun ExerciseScreen(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val exercise = data.exercises.find { it.id == id }
    var delete by remember { mutableStateOf(false) }
    var pendingSession by remember { mutableStateOf<Pair<WorkoutDetail, ExerciseDetail>?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val records = data.history.mapNotNull { workout -> workout.exercises.find { it.session.exerciseId == id }?.let { workout to it } }
    Page {
        item { PageTitle(exercise?.name ?: "Exercício", "Histórico por exercício", back); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { go("edit-exercise?exerciseId=$id") }) { Text("Editar cadastro") }; if (exercise?.archived == false) TextButton(onClick = { delete = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } }; if (!exercise?.notes.isNullOrBlank()) Text(exercise!!.notes, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Section("Último treino") }
        if (records.isEmpty()) item { EmptyState("Sem sessões anteriores", "Adicione este exercício a um treino e registre suas séries.") }
        else item { ExerciseRecord(records.first().first, records.first().second) { go("detail/${records.first().first.session.id}") } }
        item { ProgressChart(exercisePoints(data, id), "Maior carga por sessão") }
        item { Section("Todas as sessões"); Text("Apenas séries concluídas entram no gráfico. As demais ficam identificadas no histórico.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(records, key = { it.second.session.id }) { (workout, session) -> ExerciseRecord(workout, session, onDelete = { pendingSession = workout to session }, deleteEnabled = !busy) { go("detail/${workout.session.id}") } }
    }
    if (delete) DeleteExerciseDialog(exercise?.name.orEmpty(), { delete = false }) { delete = false; vm.deleteExercise(id, returnToCatalog = true) }
    pendingSession?.let { (workout, detail) -> DeleteExerciseHistoryDialog(workout, detail, busy, { pendingSession = null }) { pendingSession = null; vm.deleteExerciseHistory(detail.session.id) } }
}
@Composable fun ExerciseRecord(workout: WorkoutDetail, detail: ExerciseDetail, onDelete: (() -> Unit)? = null, deleteEnabled: Boolean = true, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(date(workout.session.startedAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onDelete != null) IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(Icons.Default.DeleteOutline, "Excluir sessão de ${detail.session.name} de ${date(workout.session.startedAt)}", tint = MaterialTheme.colorScheme.error)
            }
        }
        Text(workout.session.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail.sets.sortedBy { it.number }.forEach { set -> Text("Série ${set.number}: ${number(set.weight)} kg × ${set.reps}${if (set.completed) "  ✓" else "  (não concluída)"}") }
        if (detail.session.notes.isNotBlank()) Text(detail.session.notes, style = MaterialTheme.typography.bodySmall)
    } }
}
@Composable fun WorkoutHistoryDetail(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit) {
    val workout = data.workouts.find { it.session.id == id }
    var deleteWorkout by remember(id) { mutableStateOf(false) }
    var pendingSession by remember(id) { mutableStateOf<ExerciseDetail?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Page {
        item { PageTitle(workout?.session?.name ?: "Treino", workout?.let { date(it.session.startedAt) }, back) }
        if (workout == null) item { EmptyState("Treino não encontrado", "Consulte seus outros registros no histórico.") }
        else {
            item { Text("${duration(workout.session.startedAt, workout.session.endedAt ?: System.currentTimeMillis())} • ${workout.completedSets} séries concluídas", color = MaterialTheme.colorScheme.primary); Text("Início: ${date(workout.session.startedAt)}\nTérmino: ${workout.session.endedAt?.let { date(it) } ?: "Em andamento"}", modifier = Modifier.padding(top = 12.dp)) }
            if (workout.session.endedAt != null) item {
                OutlinedButton(onClick = { deleteWorkout = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp)); Text("Excluir treino do histórico", color = MaterialTheme.colorScheme.error)
                }
            }
            if (workout.exercises.isEmpty()) item { EmptyState("Sem exercícios neste registro", "Você pode excluir o registro do treino pelo botão acima.") }
            items(workout.exercises.sortedBy { it.session.position }, key = { it.session.id }) { exercise -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(exercise.session.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (workout.session.endedAt != null) IconButton(onClick = { pendingSession = exercise }, enabled = !busy) {
                        Icon(Icons.Default.DeleteOutline, "Excluir sessão de ${exercise.session.name} de ${date(workout.session.startedAt)}", tint = MaterialTheme.colorScheme.error)
                    }
                }
                exercise.sets.sortedBy { it.number }.forEach { set -> Text("Série ${set.number} • ${number(set.weight)} kg × ${set.reps} ${if (set.completed) "✓" else "(não concluída)"}") }
                if (exercise.session.notes.isNotEmpty()) Text("Observação: ${exercise.session.notes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } } }
        }
    }
    if (deleteWorkout && workout != null) DeleteWorkoutHistoryDialog(workout, busy, { deleteWorkout = false }) { deleteWorkout = false; vm.deleteWorkoutHistory(id, leaveDetail = true) }
    pendingSession?.let { detail -> if (workout != null) DeleteExerciseHistoryDialog(workout, detail, busy, { pendingSession = null }) { pendingSession = null; vm.deleteExerciseHistory(detail.session.id) } }
}
@Composable fun DeleteWorkoutHistoryDialog(workout: WorkoutDetail, busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Excluir registro do treino?") },
        text = { Text("${workout.session.name}\n${date(workout.session.startedAt)}\n\nEste treino e suas ${workout.exercises.sumOf { it.sets.size }} séries serão apagados do histórico. Os outros treinos e seus exercícios cadastrados serão preservados. Esta exclusão é permanente.") },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text("Excluir registro", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable fun DeleteExerciseHistoryDialog(workout: WorkoutDetail, detail: ExerciseDetail, busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Excluir sessão do exercício?") },
        text = { Text("${detail.session.name}\n${date(workout.session.startedAt)} • ${workout.session.name}\n\nEsta sessão e suas ${detail.sets.size} séries serão apagadas. As outras sessões e os demais exercícios deste treino serão preservados. Esta exclusão é permanente.") },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text("Excluir sessão", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
