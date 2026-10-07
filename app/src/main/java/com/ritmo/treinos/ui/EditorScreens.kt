package com.ritmo.treinos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun ExerciseEditor(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit) {
    val current = data.exercises.find { it.id == id }
    var name by rememberSaveable(id) { mutableStateOf(current?.name ?: "") }
    var notes by rememberSaveable(id) { mutableStateOf(current?.notes ?: "") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Page {
        item { PageTitle(if (id == 0L) "Novo exercício" else "Editar exercício", "O histórico fica sempre ligado a este cadastro.", back) }
        item { OutlinedTextField(name, { name = it.take(100) }, label = { Text("Nome do exercício") }, placeholder = { Text("Ex.: Supino reto") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Observações (opcional)") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { vm.saveExercise(id.takeIf { it > 0 }, name, notes) }, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Salvar exercício") } }
        item { Text("Se já existir um exercício com esse nome, ele será reutilizado ao cadastrar.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun WorkoutEditor(data: AppData, id: Long, vm: RitmoViewModel, back: () -> Unit, go: (String) -> Unit) {
    val current = data.templates.find { it.template.id == id }
    var name by rememberSaveable(id) { mutableStateOf(current?.template?.name ?: "") }
    var selected by rememberSaveable(id) { mutableStateOf(current?.links?.sortedBy { it.position }?.map { it.exerciseId }?.toLongArray() ?: longArrayOf()) }
    var delete by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Page {
        item { PageTitle(if (id == 0L) "Criar treino" else "Editar treino", "Escolha exercícios e organize a ordem.", back) }
        item { OutlinedTextField(name, { name = it.take(100) }, label = { Text("Nome do treino") }, placeholder = { Text("Ex.: Treino A") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Section("Exercícios do treino") }
        if (selected.isEmpty()) item { Text("Selecione os exercícios abaixo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(selected.toList(), key = { it }) { exerciseId ->
            val index = selected.indexOf(exerciseId)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("${index + 1}. ${data.exercises.find { it.id == exerciseId }?.name ?: "Exercício"}", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = { val list = selected.toMutableList(); java.util.Collections.swap(list, index, index - 1); selected = list.toLongArray() }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Mover para cima") }
                    IconButton(onClick = { val list = selected.toMutableList(); java.util.Collections.swap(list, index, index + 1); selected = list.toLongArray() }, enabled = index < selected.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Mover para baixo") }
                    IconButton(onClick = { selected = selected.filter { it != exerciseId }.toLongArray() }) { Icon(Icons.Default.Close, "Remover do treino") }
                }
            } }
        }
        item { Section("Adicionar do catálogo"); TextButton(onClick = { go("edit-exercise") }) { Icon(Icons.Default.Add, null); Text("Cadastrar exercício") } }
        items(data.exercises.filter { !it.archived && it.id !in selected }, key = { "catalog-${it.id}" }) { exercise ->
            OutlinedCard(onClick = { selected = selected + exercise.id }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(exercise.name, Modifier.weight(1f)); Icon(Icons.Default.Add, "Adicionar ao treino") } }
        }
        item { Button(onClick = { vm.saveTemplate(id.takeIf { it > 0 }, name, selected.toList()) }, enabled = name.isNotBlank() && selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Salvar treino") } }
        if (id > 0) item { TextButton(onClick = { delete = true }) { Text("Excluir modelo de treino", color = MaterialTheme.colorScheme.error) } }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Excluir este modelo?") }, text = { Text("O histórico de treinos realizados será preservado.") }, confirmButton = { TextButton(onClick = { delete = false; vm.deleteTemplate(id) }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancelar") } })
}
