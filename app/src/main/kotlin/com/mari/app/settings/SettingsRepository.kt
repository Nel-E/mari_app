package com.mari.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.phoneSettingsDataStore by preferencesDataStore(name = "phone_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<PhoneSettings> = context.phoneSettingsDataStore.data.map { prefs ->
        PhoneSettings(
            shakeStrength = prefs[KEY_SHAKE_STRENGTH] ?: 15f,
            shakeDurationMs = (prefs[KEY_SHAKE_DURATION_MS] ?: 300).toLong(),
            shakeSoundUri = prefs[KEY_SHAKE_SOUND_URI],
            shakeVibrate = prefs[KEY_SHAKE_VIBRATE] ?: true,
            reminderEnabled = prefs[KEY_REMINDER_ENABLED] ?: false,
            reminderIntervalMinutes = prefs[KEY_REMINDER_INTERVAL_MINUTES] ?: 30,
            reminderSoundUri = prefs[KEY_REMINDER_SOUND_URI],
            reminderVibrate = prefs[KEY_REMINDER_VIBRATE] ?: true,
            quietStartHour = prefs[KEY_QUIET_START_HOUR] ?: 22,
            quietStartMinute = prefs[KEY_QUIET_START_MINUTE] ?: 0,
            quietEndHour = prefs[KEY_QUIET_END_HOUR] ?: 7,
            quietEndMinute = prefs[KEY_QUIET_END_MINUTE] ?: 0,
        )
    }

    suspend fun current(): PhoneSettings = settings.first()

    suspend fun updateShakeStrength(value: Float) {
        context.phoneSettingsDataStore.edit { it[KEY_SHAKE_STRENGTH] = value.coerceIn(10f, 30f) }
    }

    suspend fun updateShakeDuration(valueMs: Long) {
        context.phoneSettingsDataStore.edit { it[KEY_SHAKE_DURATION_MS] = valueMs.coerceIn(100L, 1000L).toInt() }
    }

    suspend fun updateShakeVibrate(enabled: Boolean) {
        context.phoneSettingsDataStore.edit { it[KEY_SHAKE_VIBRATE] = enabled }
    }

    suspend fun updateReminderEnabled(enabled: Boolean) {
        context.phoneSettingsDataStore.edit { it[KEY_REMINDER_ENABLED] = enabled }
    }

    suspend fun updateReminderIntervalMinutes(minutes: Int) {
        context.phoneSettingsDataStore.edit { it[KEY_REMINDER_INTERVAL_MINUTES] = minutes.coerceIn(1, 24 * 60) }
    }

    suspend fun updateReminderVibrate(enabled: Boolean) {
        context.phoneSettingsDataStore.edit { it[KEY_REMINDER_VIBRATE] = enabled }
    }

    suspend fun updateShakeSoundUri(uri: String?) {
        context.phoneSettingsDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_SHAKE_SOUND_URI) else prefs[KEY_SHAKE_SOUND_URI] = uri
        }
    }

    suspend fun updateReminderSoundUri(uri: String?) {
        context.phoneSettingsDataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_REMINDER_SOUND_URI) else prefs[KEY_REMINDER_SOUND_URI] = uri
        }
    }

    suspend fun updateQuietHours(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
    ) {
        context.phoneSettingsDataStore.edit {
            it[KEY_QUIET_START_HOUR] = startHour.coerceIn(0, 23)
            it[KEY_QUIET_START_MINUTE] = startMinute.coerceIn(0, 59)
            it[KEY_QUIET_END_HOUR] = endHour.coerceIn(0, 23)
            it[KEY_QUIET_END_MINUTE] = endMinute.coerceIn(0, 59)
        }
    }

    private companion object {
        val KEY_SHAKE_STRENGTH = floatPreferencesKey("shake_strength")
        val KEY_SHAKE_DURATION_MS = intPreferencesKey("shake_duration_ms")
        val KEY_SHAKE_SOUND_URI = stringPreferencesKey("shake_sound_uri")
        val KEY_SHAKE_VIBRATE = booleanPreferencesKey("shake_vibrate")
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        val KEY_REMINDER_SOUND_URI = stringPreferencesKey("reminder_sound_uri")
        val KEY_REMINDER_VIBRATE = booleanPreferencesKey("reminder_vibrate")
        val KEY_QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val KEY_QUIET_START_MINUTE = intPreferencesKey("quiet_start_minute")
        val KEY_QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val KEY_QUIET_END_MINUTE = intPreferencesKey("quiet_end_minute")
    }
}
