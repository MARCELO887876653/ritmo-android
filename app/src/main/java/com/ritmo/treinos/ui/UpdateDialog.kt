package com.ritmo.treinos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ritmo.treinos.update.*
import java.util.Locale

@Composable fun UpdateDialog(info: UpdateInfo, mandatory: Boolean, download: ApkDownloadState,
    checkError: String?, installError: String?, onDownload: () -> Unit, onCancelDownload: () -> Unit,
    onInstall: () -> Unit, onLater: () -> Unit, onCheck: () -> Unit) {
    val state = download.takeIf { it.info?.sameApk(info) == true } ?: ApkDownloadState()
    val active = state.phase == DownloadPhase.DOWNLOADING || state.phase == DownloadPhase.PAUSED
    AlertDialog(onDismissRequest = { if (!mandatory) onLater() },
        title = { Text(if (mandatory) "Atualização necessária" else "Nova atualização disponível") },
        text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (mandatory) "Você precisa atualizar o aplicativo para continuar." else "A versão ${info.versionName} já está disponível.")
            Text(info.message)
            when (state.phase) {
                DownloadPhase.DOWNLOADING, DownloadPhase.PAUSED -> {
                    Text(if (state.phase == DownloadPhase.PAUSED) "Download pausado. A retomada será automática." else "Baixando atualização…")
                    val progress = state.progress
                    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(if (state.total > 0) "${(progress!! * 100).toInt()}% • ${downloadSize(state.downloaded)} de ${downloadSize(state.total)}" else downloadSize(state.downloaded), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancelDownload) { Text("Cancelar download") }
                }
                DownloadPhase.READY -> {
                    Text("Download concluído", color = MaterialTheme.colorScheme.primary)
                    Text("Toque em Instalar atualização. O Android pedirá sua confirmação e, se necessário, permissão para o Ritmo instalar atualizações.", style = MaterialTheme.typography.bodySmall)
                }
                DownloadPhase.FAILED -> Text(state.error ?: "O download falhou. Tente novamente.", color = MaterialTheme.colorScheme.error)
                DownloadPhase.IDLE -> Text("O APK será baixado aqui no aplicativo. Seus treinos serão preservados.", style = MaterialTheme.typography.bodySmall)
            }
            if (installError != null) Text(installError, color = MaterialTheme.colorScheme.error)
            if (checkError != null) Text(if (mandatory) "Não foi possível verificar novamente. A exigência de atualização foi confirmada em uma consulta anterior." else "Não foi possível verificar novamente. Você pode continuar usando o app.", style = MaterialTheme.typography.bodySmall)
            if (mandatory && !active) TextButton(onClick = onCheck) { Text("Verificar novamente") }
        } },
        confirmButton = { Button(onClick = if (state.phase == DownloadPhase.READY) onInstall else onDownload, enabled = !active) {
            Text(when (state.phase) {
                DownloadPhase.READY -> "Instalar atualização"
                DownloadPhase.FAILED -> "Tentar novamente"
                DownloadPhase.DOWNLOADING, DownloadPhase.PAUSED -> "Baixando…"
                DownloadPhase.IDLE -> if (mandatory) "Atualizar" else "Atualizar agora"
            })
        } },
        dismissButton = { if (!mandatory) TextButton(onClick = onLater) { Text("Depois") } })
}
private fun downloadSize(bytes: Long): String = if (bytes < 1024 * 1024) "${bytes.coerceAtLeast(0) / 1024} KB" else String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes.toDouble() / (1024 * 1024))
