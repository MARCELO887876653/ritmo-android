package com.ritmo.treinos

import android.app.Application
import com.ritmo.treinos.data.*
import com.ritmo.treinos.update.UpdateManager

class RitmoApplication : Application() {
    val database by lazy { RitmoDatabase.open(this) }
    val repository by lazy { WorkoutRepository(database) }
    val settings by lazy { SettingsStore(this) }
    val updates by lazy { UpdateManager(settings.prefs) }
    val backup by lazy { BackupManager(this, database) }
}
