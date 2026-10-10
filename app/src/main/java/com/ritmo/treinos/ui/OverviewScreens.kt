package com.ritmo.treinos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritmo.treinos.data.*

@Composable fun HomeScreen(data: AppData, vm: RitmoViewModel, go: (String) -> Unit) {
    val today = remember(data.templates, data.workouts) {
        val last = data.history.firstOrNull()?.session?.templateId
        val index = data.templates.indexOfFirst { it.template.id == last }
        data.templates.getOrNull(if (index < 0) 0 else (index + 1) % data.templates.size.coerceAtLeast(1))
    }
    Page {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("RITMO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Text("Seu treino.\nSeu ritmo.", style = MaterialTheme.typography.headlineLarge) }
                IconButton(onClick = { go("settings") }) { Icon(Icons.Default.Settings, "Configurações") }
            }
            Text(date(System.currentTimeMillis(), "EEEE, dd 'de' MMMM"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            val colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer)
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(colors), RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(if (data.active != null) "TREINO EM ANDAMENTO" else "TREINO DE HOJE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(data.active?.session?.name ?: today?.template?.name ?: "Vamos começar?", style = MaterialTheme.typography.headlineMedium)
                Text(if (data.active != null) "Seu progresso está salvo. Continue de onde parou." else if (today != null) "${today.links.size} exercícios • sugestão da sua sequência" else "Monte sua rotina e registre cada série.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { if (data.active != null) go("session/${data.active!!.session.id}") else if (today != null) vm.start(today.template.id) else go("edit-workout") }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (data.active != null) "Continuar treino" else if (today != null) "Começar treino" else "Criar meu primeiro treino") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("${data.history.size}", "treinos registrados", Modifier.weight(1f))
                MetricCard("${data.history.sumOf { it.completedSets }}", "séries concluídas", Modifier.weight(1f))
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { go("workouts") }, modifier = Modifier.weight(1f)) { Text("Meus treinos") }; OutlinedButton(onClick = { go("exercises") }, modifier = Modifier.weight(1f)) { Text("Exercícios") } } }
        item { Card(onClick={go("ranking")},modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { Icon(Icons.Default.EmojiEvents,null,tint=MaterialTheme.colorScheme.primary); Column(Modifier.weight(1f)) {Text("Ranking global",style=MaterialTheme.typography.titleLarge); Text("Seu ritmo, conectado ao mundo",color=MaterialTheme.colorScheme.onSurfaceVariant)}; Icon(Icons.Default.ChevronRight,null) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Section("Últimos treinos"); TextButton(onClick = { go("history") }) { Text("Ver todos") } } }
        if (data.history.isEmpty()) item { EmptyState("Seu histórico começa aqui", "Ao finalizar um treino, suas séries e observações aparecem aqui.") }
        items(data.history.take(3), key = { it.session.id }) { WorkoutCard(it) { go("detail/${it.session.id}") } }
    }
}
@Composable fun MetricCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable fun WorkoutCard(workout: WorkoutDetail, onDelete: (() -> Unit)? = null, deleteEnabled: Boolean = true, click: () -> Unit) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(workout.session.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onDelete != null) IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(Icons.Default.DeleteOutline, "Excluir treino ${workout.session.name} de ${date(workout.session.startedAt)} do histórico", tint = MaterialTheme.colorScheme.error)
            }
        }
        Text(date(workout.session.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${duration(workout.session.startedAt, workout.session.endedAt ?: System.currentTimeMillis())} • ${workout.exercises.size} exercícios • ${workout.completedSets} séries", style = MaterialTheme.typography.bodySmall)
        Text(workout.exercises.sortedBy { it.session.position }.joinToString(" • ") { it.session.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
@Composable fun WorkoutsScreen(data: AppData, vm: RitmoViewModel, go: (String) -> Unit) {
    Page {
        item { PageTitle("Meus treinos", "Sua rotina, organizada do seu jeito."); Button(onClick = { go("edit-workout") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Add, null); Text("Criar treino") } }
        if (data.templates.isEmpty()) item { EmptyState("Crie o Treino A", "Adicione exercícios do seu catálogo. O mesmo exercício pode participar de vários treinos.") }
        items(data.templates, key = { it.template.id }) { detail -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(detail.template.name, style = MaterialTheme.typography.titleLarge)
            Text(detail.links.sortedBy { it.position }.mapNotNull { link -> data.exercises.find { it.id == link.exerciseId }?.name }.joinToString(" • "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { vm.start(detail.template.id) }) { Text(if (data.active == null) "Começar" else "Continuar atual") }; OutlinedButton(onClick = { go("edit-workout?workoutId=${detail.template.id}") }) { Text("Editar") } }
        } } }
    }
}
@Composable fun ExercisesScreen(data: AppData, go: (String) -> Unit, delete: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }
    Page {
        item { PageTitle("Exercícios", "Um exercício. Todo o seu histórico."); Button(onClick = { go("edit-exercise") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Add, null); Text("Novo exercício") }; OutlinedTextField(query, { query = it }, label = { Text("Buscar exercício") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true) }
        val filtered = data.exercises.filter { !it.archived && it.name.contains(query, true) }
        if (filtered.isEmpty()) item { EmptyState("Nenhum exercício aqui", "Cadastre um exercício para usar nos seus treinos.") }
        items(filtered, key = { it.id }) { exercise ->
            val records = data.history.count { it.exercises.any { es -> es.session.exerciseId == exercise.id } }
            Card(onClick = { go("exercise/${exercise.id}") }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 16.dp)) { Text(exercise.name, style = MaterialTheme.typography.titleMedium); Text("$records sessões registradas", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = { pendingDelete = exercise }) {
                    Icon(Icons.Default.DeleteOutline, "Excluir exercício ${exercise.name}", tint = MaterialTheme.colorScheme.error)
                }
                Icon(Icons.Default.ChevronRight, null)
            } }
        }
    }
    pendingDelete?.let { exercise ->
        DeleteExerciseDialog(exercise.name, { pendingDelete = null }) {
            pendingDelete = null
            delete(exercise.id)
        }
    }
}
@Composable fun DeleteExerciseDialog(name: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Excluir exercício?") },
        text = { Text("\"$name\" será removido da lista de exercícios e dos modelos de treino. O histórico e o treino em andamento serão preservados.") },
        confirmButton = { TextButton(onClick = confirm) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable fun HistoryScreen(data: AppData, vm: RitmoViewModel, go: (String) -> Unit) {
    var pendingDelete by remember { mutableStateOf<WorkoutDetail?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Page {
        item { PageTitle("Histórico", "Cada treino conta uma parte da sua jornada.") }
        if (data.history.isEmpty()) item { EmptyState("Ainda sem registros", "Finalize seu primeiro treino para vê-lo aqui.") }
        items(data.history, key = { it.session.id }) { workout -> WorkoutCard(workout, onDelete = { pendingDelete = workout }, deleteEnabled = !busy) { go("detail/${workout.session.id}") } }
    }
    pendingDelete?.let { workout -> DeleteWorkoutHistoryDialog(workout, busy, { pendingDelete = null }) { pendingDelete = null; vm.deleteWorkoutHistory(workout.session.id) } }
}
@Composable fun ProgressScreen(data: AppData, go: (String) -> Unit) {
    Page {
        item { PageTitle("Progresso", "Acompanhe as cargas que você registrou.") }
        if (data.history.isEmpty()) item { EmptyState("Um passo de cada vez", "Os gráficos aparecem após registrar séries concluídas. Escolha um exercício para consultar os detalhes.") }
        items(data.exercises.filter { !it.archived }, key = { it.id }) { exercise ->
            val points = exercisePoints(data, exercise.id)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { ProgressChart(points, exercise.name); TextButton(onClick = { go("exercise/${exercise.id}") }) { Text("Ver histórico de ${exercise.name}") } }
        }
    }
}
fun exercisePoints(data: AppData, id: Long): List<Pair<Long, Double>> = data.history.sortedBy { it.session.startedAt }.mapNotNull { workout ->
    val weights = workout.exercises.filter { it.session.exerciseId == id }.flatMap { it.sets }.filter { it.completed }.map { it.weight }
    weights.maxOrNull()?.let { workout.session.startedAt to it }
}
