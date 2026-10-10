package com.ritmo.treinos

import android.app.Application
import com.ritmo.treinos.data.*
import com.ritmo.treinos.update.UpdateManager
import com.ritmo.treinos.update.ApkDownloads

class RitmoApplication : Application() {
    val database by lazy { RitmoDatabase.open(this) }
    val online by lazy { com.ritmo.treinos.online.OnlineService(this) }
    val rankingSync by lazy { com.ritmo.treinos.online.RankingSynchronizer(database, online) }
    val repository by lazy { WorkoutRepository(database, online::rankingOwner) }
    val settings by lazy { SettingsStore(this) }
    val updates by lazy { UpdateManager(settings.prefs) }
    val apkDownloads by lazy { ApkDownloads(this, settings.prefs) }
    val backup by lazy { BackupManager(this, database) }
}
