package com.ritmo.treinos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ritmo.treinos.data.*

@Composable fun ExerciseScreen(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val exercise = data.exercises.find { it.id == id }
    var archive by remember { mutableStateOf(false) }
    val records = data.history.mapNotNull { workout -> workout.exercises.find { it.session.exerciseId == id }?.let { workout to it } }
    Page {
        item { PageTitle(exercise?.name ?: "Exercício", "Histórico por exercício", back); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { go("edit-exercise?exerciseId=$id") }) { Text("Editar cadastro") }; if (exercise?.archived == false) TextButton(onClick = { archive = true }) { Text("Arquivar") } }; if (!exercise?.notes.isNullOrBlank()) Text(exercise!!.notes, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Section("Último treino") }
        if (records.isEmpty()) item { EmptyState("Sem sessões anteriores", "Adicione este exercício a um treino e registre suas séries.") }
        else item { ExerciseRecord(records.first().first, records.first().second) { go("detail/${records.first().first.session.id}") } }
        item { ProgressChart(exercisePoints(data, id), "Maior carga por sessão") }
        item { Section("Todas as sessões"); Text("Apenas séries concluídas entram no gráfico. As demais ficam identificadas no histórico.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(records, key = { it.second.session.id }) { (workout, session) -> ExerciseRecord(workout, session) { go("detail/${workout.session.id}") } }
    }
    if (archive) AlertDialog(onDismissRequest = { archive = false }, title = { Text("Arquivar exercício?") }, text = { Text("Ele sairá do catálogo para novos treinos. Seus registros e os modelos que já usam o exercício serão preservados. Cadastre o mesmo nome para reativá-lo.") }, confirmButton = { TextButton(onClick = { archive = false; vm.archiveExercise(id) }) { Text("Arquivar") } }, dismissButton = { TextButton(onClick = { archive = false }) { Text("Cancelar") } })
}
@Composable fun ExerciseRecord(workout: WorkoutDetail, detail: ExerciseDetail, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date(workout.session.startedAt), style = MaterialTheme.typography.titleMedium)
        Text(workout.session.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail.sets.sortedBy { it.number }.forEach { set -> Text("Série ${set.number}: ${number(set.weight)} kg × ${set.reps}${if (set.completed) "  ✓" else "  (não concluída)"}") }
        if (detail.session.notes.isNotBlank()) Text(detail.session.notes, style = MaterialTheme.typography.bodySmall)
    } }
}
@Composable fun WorkoutHistoryDetail(data: AppData, id: Long, back: () -> Unit) {
    val workout = data.workouts.find { it.session.id == id }
    Page {
        item { PageTitle(workout?.session?.name ?: "Treino", workout?.let { date(it.session.startedAt) }, back) }
        if (workout == null) item { EmptyState("Treino não encontrado", "Consulte seus outros registros no histórico.") }
        else {
            item { Text("${duration(workout.session.startedAt, workout.session.endedAt ?: System.currentTimeMillis())} • ${workout.completedSets} séries concluídas", color = MaterialTheme.colorScheme.primary); Text("Início: ${date(workout.session.startedAt)}\nTérmino: ${workout.session.endedAt?.let { date(it) } ?: "Em andamento"}", modifier = Modifier.padding(top = 12.dp)) }
            items(workout.exercises.sortedBy { it.session.position }, key = { it.session.id }) { exercise -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise.session.name, style = MaterialTheme.typography.titleLarge)
                exercise.sets.sortedBy { it.number }.forEach { set -> Text("Série ${set.number} • ${number(set.weight)} kg × ${set.reps} ${if (set.completed) "✓" else "(não concluída)"}") }
                if (exercise.session.notes.isNotEmpty()) Text("Observação: ${exercise.session.notes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } } }
        }
    }
}
