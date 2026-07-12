package com.shasan731.networkinvestigator.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("network_investigator_settings")

enum class ThemePreference { SYSTEM, LIGHT, DARK }
data class UserSettings(val theme: ThemePreference = ThemePreference.SYSTEM, val dynamicColor: Boolean = true, val offlineOnly: Boolean = true, val onlineEnrichment: Boolean = false, val retentionDays: Int? = 90, val diagnosticConcurrency: Int = 4, val biometricLock: Boolean = false)

class AppPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme"); val dynamic = booleanPreferencesKey("dynamic_color")
        val offline = booleanPreferencesKey("offline_only"); val enrichment = booleanPreferencesKey("online_enrichment")
        val retention = intPreferencesKey("retention_days"); val concurrency = intPreferencesKey("diagnostic_concurrency")
        val biometric = booleanPreferencesKey("biometric_lock")
    }
    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        val storedRetention = p[Keys.retention]
        UserSettings(ThemePreference.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemePreference.SYSTEM, p[Keys.dynamic] ?: true, p[Keys.offline] ?: true, p[Keys.enrichment] ?: false, when { storedRetention == null -> 90; storedRetention <= 0 -> null; else -> storedRetention }, p[Keys.concurrency] ?: 4, p[Keys.biometric] ?: false)
    }
    suspend fun setOfflineOnly(value: Boolean) = context.dataStore.edit { it[Keys.offline] = value; if (value) it[Keys.enrichment] = false }
    suspend fun setTheme(value: ThemePreference) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setOnlineEnrichment(value: Boolean) = context.dataStore.edit { it[Keys.enrichment] = value; if (value) it[Keys.offline] = false }
    suspend fun setRetentionDays(value: Int?) = context.dataStore.edit { it[Keys.retention] = value ?: -1 }
    suspend fun setBiometricLock(value: Boolean) = context.dataStore.edit { it[Keys.biometric] = value }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
