package com.ritmo.treinos.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(val theme: String = "dark", val restSeconds: Int = 90, val autoRest: Boolean = true, val checkOnOpen: Boolean = true)
class SettingsStore(context: Context) {
    val prefs = context.getSharedPreferences("ritmo-settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AppSettings(prefs.getString("theme", "dark") ?: "dark", prefs.getInt("rest", 90), prefs.getBoolean("autoRest", true), prefs.getBoolean("check", true)))
    val settings = state.asStateFlow()
    fun save(value: AppSettings) { prefs.edit().putString("theme", value.theme).putInt("rest", value.restSeconds).putBoolean("autoRest", value.autoRest).putBoolean("check", value.checkOnOpen).apply(); state.value = value }
    fun restDeadline() = prefs.getLong("restDeadline", 0)
    fun setRestDeadline(value: Long) { prefs.edit().putLong("restDeadline", value).apply() }
}
