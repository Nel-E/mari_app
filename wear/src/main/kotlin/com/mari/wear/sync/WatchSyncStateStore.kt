package com.mari.wear.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.watchSyncDataStore by preferencesDataStore(name = "watch_sync_state")

@Singleton
class WatchSyncStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun versions(): Map<String, Int> {
        val raw = context.watchSyncDataStore.data.first()[KEY_SYNC_VERSIONS] ?: return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    suspend fun markSynced(upToVersion: Map<String, Int>) {
        if (upToVersion.isEmpty()) return
        val current = versions().toMutableMap()
        upToVersion.forEach { (id, version) ->
            val existing = current[id]
            if (existing == null || version >= existing) current[id] = version
        }
        context.watchSyncDataStore.edit {
            it[KEY_SYNC_VERSIONS] = Json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), current)
        }
    }

    private companion object {
        val KEY_SYNC_VERSIONS = stringPreferencesKey("sync_versions")
    }
}
