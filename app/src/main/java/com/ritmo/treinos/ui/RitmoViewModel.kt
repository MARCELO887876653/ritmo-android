package com.ritmo.treinos.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ritmo.treinos.RitmoApplication
import com.ritmo.treinos.BuildConfig
import com.ritmo.treinos.data.*
import com.ritmo.treinos.update.UpdateInfo
import com.ritmo.treinos.update.ApkDownloadState
import com.ritmo.treinos.update.DownloadPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppData(val exercises: List<Exercise> = emptyList(), val templates: List<TemplateDetail> = emptyList(), val workouts: List<WorkoutDetail> = emptyList(), val loaded: Boolean = false) {
    val active get() = workouts.firstOrNull { it.session.endedAt == null }
    val history get() = workouts.filter { it.session.endedAt != null }
}
sealed interface UiEvent {
    data class Install(val intent: android.content.Intent, val needsPermission: Boolean) : UiEvent
    data class Message(val text: String) : UiEvent
    data class Navigate(val route: String, val home: Boolean = false) : UiEvent
}
class RitmoViewModel(private val app: RitmoApplication) : ViewModel() {
    private val repo = app.repository
    private val mutex = Mutex()
    val data = combine(repo.exercises, repo.templates, repo.workouts) { e, t, w -> AppData(e, t, w, true) }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())
    val settings = app.settings.settings
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val busy = MutableStateFlow(false)
    val update = MutableStateFlow(app.updates.cached())
    val checking = MutableStateFlow(false)
    val updateError = MutableStateFlow<String?>(null)
    val dismissedCode = MutableStateFlow<Int?>(null)
    val apkDownload = MutableStateFlow(ApkDownloadState())
    val installError = MutableStateFlow<String?>(null)
    private val downloadMutex = Mutex()
    val restRemaining = MutableStateFlow(0)
    private var deadline = app.settings.restDeadline()
    init {
        viewModelScope.launch {
            var restoreDownload = true
            while (isActive) {
                if (restoreDownload || apkDownload.value.phase in listOf(DownloadPhase.DOWNLOADING, DownloadPhase.PAUSED)) {
                    downloadMutex.withLock { apkDownload.value = withContext(Dispatchers.IO) { app.apkDownloads.refresh() } }
                    restoreDownload = false
                }
                delay(1000)
            }
        }
        if (settings.value.checkOnOpen || update.value?.mandatory(BuildConfig.VERSION_CODE) == true) checkUpdate(false)
        viewModelScope.launch {
            while (isActive) {
                val left = if (deadline == 0L) 0 else ((deadline - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0).toInt()
                if (restRemaining.value > 0 && left == 0) { deadline = 0; app.settings.setRestDeadline(0); eventChannel.send(UiEvent.Message("Descanso concluído.")) }
                restRemaining.value = left
                delay(250)
            }
        }
    }
    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        mutex.withLock {
            busy.value = true
            try { block() } catch (e: Exception) { if (e is CancellationException) throw e; eventChannel.send(UiEvent.Message(e.message ?: "Não foi possível salvar.")) }
            finally { busy.value = false }
        }
    }
    fun saveExercise(id: Long?, name: String, notes: String) = work { val result = repo.saveExercise(id, name, notes); eventChannel.send(UiEvent.Navigate("exercise/$result")) }
    fun archiveExercise(id: Long) = work { repo.archiveExercise(id); eventChannel.send(UiEvent.Navigate("exercises")) }
    fun deleteExercise(id: Long, returnToCatalog: Boolean = false) = work {
        repo.deleteExercise(id)
        if (returnToCatalog) eventChannel.send(UiEvent.Navigate("exercises"))
        eventChannel.send(UiEvent.Message("Exercício excluído. Os registros anteriores foram preservados."))
    }
    fun saveTemplate(id: Long?, name: String, exerciseIds: List<Long>) = work { repo.saveTemplate(id, name, exerciseIds); eventChannel.send(UiEvent.Navigate("workouts")) }
    fun deleteTemplate(id: Long) = work { repo.deleteTemplate(id); eventChannel.send(UiEvent.Navigate("workouts")) }
    fun start(id: Long) = work { eventChannel.send(UiEvent.Navigate("session/${repo.start(id)}")) }
    fun addSet(id: Long) = work { repo.addSet(id) }
    fun removeSet(id: Long) = work { repo.removeSet(id) }
    fun saveSet(id: Long, weight: Double, reps: Int, completed: Boolean, startRest: Boolean = false) = work {
        repo.saveSet(id, weight, reps, completed)
        if (startRest && completed && settings.value.autoRest) startRest()
    }
    fun note(id: Long, text: String) = work { repo.note(id, text) }
    fun copyPrevious(id: Long) = work { repo.copyPrevious(id) }
    fun finish(id: Long) = work { repo.finish(id); stopRest(); eventChannel.send(UiEvent.Navigate("detail/$id", true)) }
    fun configure(value: AppSettings) { app.settings.save(value) }
    fun startRest(seconds: Int = settings.value.restSeconds) {
        deadline = System.currentTimeMillis() + seconds.coerceIn(1, 3600) * 1000L
        app.settings.setRestDeadline(deadline); restRemaining.value = seconds.coerceIn(1, 3600)
    }
    fun stopRest() { deadline = 0; app.settings.setRestDeadline(0); restRemaining.value = 0 }
    fun checkUpdate(manual: Boolean = true) {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            try {
                val result = app.updates.check(); update.value = result.info; updateError.value = result.error
                if (manual) { dismissedCode.value = null; eventChannel.send(UiEvent.Message(result.error ?: if (result.info?.available(BuildConfig.VERSION_CODE) == true) "Nova atualização disponível." else "Você está na versão mais recente.")) }
            } finally { checking.value = false }
        }
    }
    fun downloadUpdate() = viewModelScope.launch {
        val target = update.value ?: return@launch
        dismissedCode.value = null
        installError.value = null
        try { downloadMutex.withLock { apkDownload.value = withContext(Dispatchers.IO) { app.apkDownloads.start(target) } } }
        catch (e: Exception) { if (e is CancellationException) throw e; installError.value = e.message ?: "Não foi possível iniciar o download." }
    }
    fun cancelDownload() = viewModelScope.launch {
        downloadMutex.withLock {
            withContext(Dispatchers.IO) { app.apkDownloads.cancel() }
            apkDownload.value = ApkDownloadState()
        }
    }
    fun installUpdate(afterPermission: Boolean = false) = viewModelScope.launch {
        installError.value = null
        try {
            val target = requireNotNull(update.value)
            val action = downloadMutex.withLock { withContext(Dispatchers.IO) { app.apkDownloads.installAction(target) } }
            if (afterPermission && action.needsPermission) installError.value = "Permita ao Ritmo instalar atualizações para continuar. O APK permanece salvo."
            else eventChannel.send(UiEvent.Install(action.intent, action.needsPermission))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            installError.value = e.message ?: "Não foi possível abrir a instalação. Tente novamente."
            downloadMutex.withLock { apkDownload.value = withContext(Dispatchers.IO) { app.apkDownloads.refresh() } }
        }
    }
    fun installLaunchFailed() { installError.value = "O Android não conseguiu abrir a instalação ou a permissão. O APK permanece salvo; tente novamente." }
    fun openUpdateDownload() { dismissedCode.value = null }
    fun export(uri: Uri) = work { app.backup.export(uri); eventChannel.send(UiEvent.Message("Backup exportado. Guarde o arquivo em um local seguro.")) }
    fun restore(uri: Uri) = work { app.backup.restore(uri); stopRest(); eventChannel.send(UiEvent.Navigate("home", true)); eventChannel.send(UiEvent.Message("Backup restaurado.")) }
    fun message(text: String) { viewModelScope.launch { eventChannel.send(UiEvent.Message(text)) } }
    companion object { fun factory(app: RitmoApplication) = object : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T { require(modelClass.isAssignableFrom(RitmoViewModel::class.java)); @Suppress("UNCHECKED_CAST") return RitmoViewModel(app) as T } } }
}
