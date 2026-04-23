package com.mari.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mari.app.domain.model.AppUpdateInfo
import com.mari.app.domain.model.AppUpdateLocalState
import com.mari.app.domain.model.AppUpdateReleaseNote
import com.mari.app.domain.model.UpdateTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appUpdateDataStore by preferencesDataStore(name = "app_update")

@Singleton
class AppUpdateLocalStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val KEY_AUTO_CHECK = booleanPreferencesKey("auto_check_enabled")
    private val KEY_TRACK = stringPreferencesKey("track")
    private val KEY_INTERVAL = intPreferencesKey("check_interval_hours")
    private val KEY_LAST_CHECK = stringPreferencesKey("last_check_at")
    private val KEY_AVAILABLE_UPDATE = stringPreferencesKey("available_update")
    private val KEY_RELEASE_NOTES = stringPreferencesKey("release_notes")
    private val KEY_LAST_NOTIFIED = intPreferencesKey("last_notified_version_code")
    private val KEY_LAST_ACKNOWLEDGED = intPreferencesKey("last_acknowledged_version_code")

    val state: Flow<AppUpdateLocalState> = context.appUpdateDataStore.data.map { prefs ->
        AppUpdateLocalState(
            autoCheckEnabled = prefs[KEY_AUTO_CHECK] ?: true,
            track = UpdateTrack.entries.find { it.wire == prefs[KEY_TRACK] } ?: UpdateTrack.STABLE,
            checkIntervalHours = prefs[KEY_INTERVAL] ?: 6,
            lastCheckAt = prefs[KEY_LAST_CHECK],
            availableUpdate = prefs[KEY_AVAILABLE_UPDATE]?.let {
                runCatching { json.decodeFromString<SerializableUpdateInfo>(it).toAppUpdateInfo() }.getOrNull()
            },
            releaseNotes = prefs[KEY_RELEASE_NOTES]?.let {
                runCatching {
                    json.decodeFromString<List<SerializableReleaseNote>>(it).map { n -> n.toAppUpdateReleaseNote() }
                }.getOrNull()
            } ?: emptyList(),
            lastNotifiedVersionCode = prefs[KEY_LAST_NOTIFIED],
            lastAcknowledgedVersionCode = prefs[KEY_LAST_ACKNOWLEDGED],
        )
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.appUpdateDataStore.edit { it[KEY_AUTO_CHECK] = enabled }
    }

    suspend fun setTrack(track: UpdateTrack) {
        context.appUpdateDataStore.edit { it[KEY_TRACK] = track.wire }
    }

    suspend fun setCheckInterval(hours: Int) {
        context.appUpdateDataStore.edit { it[KEY_INTERVAL] = hours.coerceAtLeast(1) }
    }

    suspend fun setLastCheckAt(iso: String) {
        context.appUpdateDataStore.edit { it[KEY_LAST_CHECK] = iso }
    }

    suspend fun setAvailableUpdate(info: AppUpdateInfo?) {
        context.appUpdateDataStore.edit { prefs ->
            if (info == null) {
                prefs.remove(KEY_AVAILABLE_UPDATE)
            } else {
                prefs[KEY_AVAILABLE_UPDATE] = json.encodeToString(info.toSerializable())
            }
        }
    }

    suspend fun setReleaseNotes(notes: List<AppUpdateReleaseNote>) {
        context.appUpdateDataStore.edit { prefs ->
            prefs[KEY_RELEASE_NOTES] = json.encodeToString(notes.map { it.toSerializable() })
        }
    }

    suspend fun setLastNotifiedVersionCode(code: Int?) {
        context.appUpdateDataStore.edit { prefs ->
            if (code == null) prefs.remove(KEY_LAST_NOTIFIED) else prefs[KEY_LAST_NOTIFIED] = code
        }
    }

    suspend fun setLastAcknowledgedVersionCode(code: Int?) {
        context.appUpdateDataStore.edit { prefs ->
            if (code == null) prefs.remove(KEY_LAST_ACKNOWLEDGED) else prefs[KEY_LAST_ACKNOWLEDGED] = code
        }
    }
}

// ---------------------------------------------------------------------------
// Serializable mirror types for DataStore persistence
// ---------------------------------------------------------------------------

@kotlinx.serialization.Serializable
private data class SerializableUpdateInfo(
    val track: String,
    val component: String,
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val releasedAt: String,
    val notificationTitle: String,
    val notificationText: String,
    val changelog: String? = null,
    val downloadUrl: String,
    val minInstalledVersionCode: Int? = null,
)

@kotlinx.serialization.Serializable
private data class SerializableReleaseNote(
    val versionCode: Int,
    val versionName: String,
    val releasedAt: String,
    val features: List<String>,
    val upgrades: List<String>,
    val fixes: List<String>,
)

private fun AppUpdateInfo.toSerializable() = SerializableUpdateInfo(
    track, component, packageName, versionCode, versionName, fileName,
    fileSizeBytes, sha256, releasedAt, notificationTitle, notificationText,
    changelog, downloadUrl, minInstalledVersionCode,
)

private fun SerializableUpdateInfo.toAppUpdateInfo() = AppUpdateInfo(
    track, component, packageName, versionCode, versionName, fileName,
    fileSizeBytes, sha256, releasedAt, notificationTitle, notificationText,
    changelog, downloadUrl, minInstalledVersionCode,
)

private fun AppUpdateReleaseNote.toSerializable() = SerializableReleaseNote(
    versionCode, versionName, releasedAt, features, upgrades, fixes,
)

private fun SerializableReleaseNote.toAppUpdateReleaseNote() = AppUpdateReleaseNote(
    versionCode, versionName, releasedAt, features, upgrades, fixes,
)
