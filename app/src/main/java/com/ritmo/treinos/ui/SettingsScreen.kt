package com.ritmo.treinos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritmo.treinos.BuildConfig
import com.ritmo.treinos.update.DownloadPhase

@Composable fun SettingsScreen(vm: RitmoViewModel, back: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val download by vm.apkDownload.collectAsStateWithLifecycle()
    var custom by rememberSaveable { mutableStateOf(settings.restSeconds.toString()) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    Page {
        item { PageTitle("Configurações", "Ritmo, do seu jeito.", back) }
        item { Section("Aparência"); Column { listOf("dark" to "Escuro", "light" to "Claro", "system" to "Sistema").forEach { (mode, text) -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = settings.theme == mode, onClick = { vm.configure(settings.copy(theme = mode)) }); Text(text) } } } }
        item { Section("Descanso"); SettingSwitch("Iniciar ao concluir série", settings.autoRest) { vm.configure(settings.copy(autoRest = it)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(30, 60, 90, 120).forEach { value -> FilterChip(selected = settings.restSeconds == value, onClick = { vm.configure(settings.copy(restSeconds = value)); custom = value.toString() }, label = { Text(if (value == 120) "2 min" else "${value}s") }) } } }
        item { OutlinedTextField(custom, { if (it.length <= 4 && it.all(Char::isDigit)) custom = it }, label = { Text("Tempo personalizado (segundos)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { custom.toIntOrNull()?.takeIf { it in 1..3600 }?.let { vm.configure(settings.copy(restSeconds = it)) } ?: vm.message("Escolha entre 1 e 3.600 segundos.") }) { Text("Aplicar tempo personalizado") }; Text("Padrão: ${settings.restSeconds}s. O tempo é preservado ao sair do app; o aviso aparece com o app aberto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Section("Atualizações"); SettingSwitch("Verificar ao abrir", settings.checkOnOpen) { vm.configure(settings.copy(checkOnOpen = it)) }; OutlinedButton(onClick = { vm.checkUpdate() }, enabled = !checking) { if (checking) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }; Text(if (checking) "Verificando…" else "Verificar agora") } }
        if (download.phase != DownloadPhase.IDLE) item {
            OutlinedButton(onClick = { vm.openUpdateDownload() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (download.phase == DownloadPhase.READY) "Atualização baixada • Instalar" else "Ver download da atualização")
            }
        }
        item { Section("Seus dados"); Text("Os treinos ficam neste aparelho. Exporte um backup antes de desinstalar ou trocar de celular.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = { export.launch("ritmo-backup-${date(System.currentTimeMillis(), "yyyy-MM-dd")}.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Exportar backup") }; OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurar backup") } }
        item { Section("Sobre o Ritmo"); Text("Organize seus treinos, registre séries e acompanhe seu próprio histórico. Sem conta, sem anúncios, sem metas corporais."); Text("Versão ${BuildConfig.VERSION_NAME}\nBuild ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)); Text("Android nativo • dados offline", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (restoreUri != null) AlertDialog(onDismissRequest = { restoreUri = null }, title = { Text("Restaurar backup?") }, text = { Text("A restauração substituirá todos os treinos deste aparelho pelos dados do arquivo. Exporte os dados atuais primeiro se quiser preservá-los. Arquivos inválidos não alteram o banco.") }, confirmButton = { TextButton(onClick = { val uri = restoreUri!!; restoreUri = null; vm.restore(uri) }) { Text("Restaurar") } }, dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancelar") } })
}
@Composable fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, change) } }
