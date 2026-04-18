package com.mari.wear.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wearSettingsDataStore by preferencesDataStore(name = "wear_settings")

@Singleton
class WearSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<WearSettings> = context.wearSettingsDataStore.data.map { prefs ->
        WearSettings(
            shakeStrength = prefs[KEY_SHAKE_STRENGTH] ?: 15f,
            shakeDurationMs = (prefs[KEY_SHAKE_DURATION_MS] ?: 300).toLong(),
            shakeVibrate = prefs[KEY_SHAKE_VIBRATE] ?: true,
            reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
            reminderIntervalMinutes = prefs[KEY_REMINDER_INTERVAL_MINUTES] ?: 30,
            reminderVibrate = prefs[KEY_REMINDER_VIBRATE] ?: true,
        )
    }

    suspend fun current(): WearSettings = settings.first()

    suspend fun updateReminderIntervalMinutes(minutes: Int) {
        context.wearSettingsDataStore.edit { it[KEY_REMINDER_INTERVAL_MINUTES] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        context.wearSettingsDataStore.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun updateShakeStrength(value: Float) {
        context.wearSettingsDataStore.edit { it[KEY_SHAKE_STRENGTH] = value.coerceIn(10f, 30f) }
    }

    suspend fun updateShakeDuration(valueMs: Long) {
        context.wearSettingsDataStore.edit { it[KEY_SHAKE_DURATION_MS] = valueMs.coerceIn(100L, 1000L).toInt() }
    }

    suspend fun updateShakeVibrate(enabled: Boolean) {
        context.wearSettingsDataStore.edit { it[KEY_SHAKE_VIBRATE] = enabled }
    }

    suspend fun updateReminderVibrate(enabled: Boolean) {
        context.wearSettingsDataStore.edit { it[KEY_REMINDER_VIBRATE] = enabled }
    }

    private companion object {
        val KEY_SHAKE_STRENGTH = floatPreferencesKey("shake_strength")
        val KEY_SHAKE_DURATION_MS = intPreferencesKey("shake_duration_ms")
        val KEY_SHAKE_VIBRATE = booleanPreferencesKey("shake_vibrate")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        val KEY_REMINDER_VIBRATE = booleanPreferencesKey("reminder_vibrate")
    }
}
