package com.ritmo.treinos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritmo.treinos.data.*
import kotlinx.coroutines.launch

@Composable fun SessionScreen(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val workout = data.workouts.find { it.session.id == id }
    val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    var finish by remember { mutableStateOf(false) }
    val rest by vm.restRemaining.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ordered = workout?.exercises?.sortedBy { it.session.position }.orEmpty()
    if (workout?.session?.endedAt != null) { WorkoutHistoryDetail(data, id, back); return }
    LazyColumn(Modifier.fillMaxSize().imePadding(), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle(workout?.session?.name ?: "Treino", "Seu progresso é salvo automaticamente.", back) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(if (rest > 0) "${rest / 60}:${(rest % 60).toString().padStart(2, '0')}" else "Descanso", style = MaterialTheme.typography.headlineSmall); Text(if (rest > 0) "Contagem em andamento" else "Opcional • escolha nas configurações", style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { if (rest > 0) vm.stopRest() else vm.startRest() }) { Text(if (rest > 0) "Parar" else "Iniciar") }
            } }
        }
        items(ordered, key = { it.session.id }) { detail ->
            val previous = data.history.firstNotNullOfOrNull { previousWorkout -> previousWorkout.exercises.firstOrNull { it.session.exerciseId == detail.session.exerciseId } }
            val previousSets = previous?.sets?.filter { it.completed }?.sortedBy { it.number }.orEmpty()
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${detail.session.position + 1} / ${ordered.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(detail.session.name, style = MaterialTheme.typography.headlineSmall)
                if (previousSets.isNotEmpty()) {
                    Text("Última vez: ${previousSets.joinToString(" • ") { "${number(it.weight)} kg × ${it.reps}" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { vm.copyPrevious(detail.session.id) }, enabled = !busy && detail.sets.none { it.completed || it.weight > 0 || it.reps > 0 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copiar séries anteriores") }
                }
                detail.sets.sortedBy { it.number }.forEach { set -> key(set.id) { SetEditor(set, vm) } }
                OutlinedButton(onClick = { vm.addSet(detail.session.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.Add, null); Text("Adicionar série") }
                NoteEditor(detail, vm)
                val next = ordered.indexOf(detail) + 1
                if (next < ordered.size) TextButton(onClick = { scope.launch { list.animateScrollToItem(2 + next) } }, modifier = Modifier.fillMaxWidth()) { Text("Próximo exercício →") }
            } }
        }
        item { Button(onClick = { finish = true }, enabled = workout != null && !busy, modifier = Modifier.fillMaxWidth().height(58.dp)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Finalizar treino") }; TextButton(onClick = { go("settings") }, modifier = Modifier.fillMaxWidth()) { Text("Ajustar descanso") } }
    }
    if (finish) AlertDialog(onDismissRequest = { finish = false }, title = { Text("Finalizar treino?") }, text = { Text("${workout?.completedSets ?: 0} séries concluídas. As séries sem marcação serão guardadas como não concluídas.") }, confirmButton = { TextButton(onClick = { finish = false; vm.finish(id) }, enabled = !busy) { Text("Finalizar") } }, dismissButton = { TextButton(onClick = { finish = false }) { Text("Continuar treinando") } })
}
@Composable fun SetEditor(set: ExerciseSet, vm: RitmoViewModel) {
    var weight by rememberSaveable(set.id) { mutableStateOf(if (set.weight == 0.0) "" else number(set.weight)) }
    var reps by rememberSaveable(set.id) { mutableStateOf(if (set.reps == 0) "" else set.reps.toString()) }
    var completed by remember(set.id, set.completed) { mutableStateOf(set.completed) }
    var remove by remember { mutableStateOf(false) }
    fun persist(check: Boolean = completed, timer: Boolean = false) {
        val w = weight.replace(',', '.').toDoubleOrNull() ?: if (weight.isEmpty()) 0.0 else null
        val r = reps.toIntOrNull() ?: if (reps.isEmpty()) 0 else null
        if (w != null && r != null && (!check || r > 0)) vm.saveSet(set.id, w, r, check, timer)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Série ${set.number}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { remove = true }) { Icon(Icons.Default.DeleteOutline, "Excluir série ${set.number}") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(weight, { if (it.matches(Regex("\\d{0,5}([.,]\\d{0,2})?"))) { weight = it; persist() } }, label = { Text("Carga (kg)") }, isError = weight.replace(',', '.').toDoubleOrNull()?.let { it > 10000 } == true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(reps, { if (it.length <= 5 && it.all(Char::isDigit)) { reps = it; persist() } }, label = { Text("Repetições") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = completed, onCheckedChange = { check ->
            val w = weight.replace(',', '.').toDoubleOrNull() ?: 0.0; val r = reps.toIntOrNull() ?: 0
            if (check && (r <= 0 || r > 10000 || w > 10000)) vm.message("Informe carga e repetições válidas antes de concluir.")
            else { completed = check; persist(check, check) }
        }); Text(if (completed) "Série concluída" else "Marcar como concluída") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Excluir série ${set.number}?") }, confirmButton = { TextButton(onClick = { remove = false; vm.removeSet(set.id) }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancelar") } })
}
@Composable fun NoteEditor(detail: ExerciseDetail, vm: RitmoViewModel) {
    var notes by rememberSaveable(detail.session.id) { mutableStateOf(detail.session.notes) }
    OutlinedTextField(notes, { notes = it.take(2000); vm.note(detail.session.id, notes) }, label = { Text("Observação deste exercício") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
}
