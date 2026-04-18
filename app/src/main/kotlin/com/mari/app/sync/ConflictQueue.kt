package com.mari.app.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mari.shared.data.sync.SyncConflict
import com.mari.shared.domain.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.conflictQueueStore by preferencesDataStore(name = "phone_conflicts")

@Serializable
private data class StoredConflict(
    val local: Task,
    val incoming: Task,
)

@Singleton
class ConflictQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val conflicts: Flow<List<SyncConflict>> = context.conflictQueueStore.data.map { prefs ->
        val raw = prefs[KEY_PENDING_CONFLICTS] ?: return@map emptyList()
        runCatching {
            Json.decodeFromString<List<StoredConflict>>(raw).map {
                SyncConflict(local = it.local, incoming = it.incoming, decision = com.mari.shared.data.sync.ConflictDecision.CONFLICT)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun enqueue(items: List<SyncConflict>) {
        if (items.isEmpty()) return
        val merged = (currentStored() + items.map { StoredConflict(it.local, it.incoming) })
            .distinctBy { it.local.id + ":" + it.incoming.version }
        persist(merged)
    }

    suspend fun remove(taskId: String) {
        persist(currentStored().filterNot { it.local.id == taskId })
    }

    private suspend fun currentStored(): List<StoredConflict> {
        val prefs = context.conflictQueueStore.data.first()
        val raw = prefs[KEY_PENDING_CONFLICTS] ?: return emptyList()
        return runCatching { Json.decodeFromString<List<StoredConflict>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun persist(items: List<StoredConflict>) {
        context.conflictQueueStore.edit { prefs ->
            if (items.isEmpty()) {
                prefs.remove(KEY_PENDING_CONFLICTS)
            } else {
                prefs[KEY_PENDING_CONFLICTS] = Json.encodeToString(items)
            }
        }
    }

    private companion object {
        val KEY_PENDING_CONFLICTS = stringPreferencesKey("pending_conflicts")
    }
}
