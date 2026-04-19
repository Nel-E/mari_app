package com.mari.app.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.sync.SyncEngine
import com.mari.shared.data.sync.SyncEnvelope
import com.mari.shared.domain.Task
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneSyncService : WearableListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PhoneSyncEntryPoint {
        fun repository(): TaskRepository
        fun client(): PhoneSyncClient
        fun conflictQueue(): ConflictQueue
        fun syncStateStore(): SyncStateStore
    }

    private lateinit var repository: TaskRepository
    private lateinit var client: PhoneSyncClient
    private lateinit var conflictQueue: ConflictQueue
    private lateinit var syncStateStore: SyncStateStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        val ep = EntryPointAccessors.fromApplication(
            applicationContext,
            PhoneSyncEntryPoint::class.java,
        )
        repository = ep.repository()
        client = ep.client()
        conflictQueue = ep.conflictQueue()
        syncStateStore = ep.syncStateStore()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val uri = item.uri
            Log.d(TAG, "onDataChanged: item uri=$uri")
            val payload = DataMapItem.fromDataItem(item).dataMap.getByteArray("payload") ?: return@forEach
            serviceScope.launch {
                handleEnvelope(SyncEnvelope.decode(payload))
                Wearable.getDataClient(this@PhoneSyncService).deleteDataItems(uri)
            }
        }
    }

    private suspend fun handleEnvelope(envelope: SyncEnvelope) {
        Log.d(TAG, "handleEnvelope: ${envelope::class.simpleName} schemaVersion=${envelope.schemaVersion}")
        if (envelope.schemaVersion != SyncEnvelope.CURRENT_SCHEMA_VERSION) return
        if (
            (envelope is SyncEnvelope.TupleManifest && envelope.deviceId == "phone") ||
            (envelope is SyncEnvelope.DeltaBundle && envelope.deviceId == "phone") ||
            (envelope is SyncEnvelope.Ack && envelope.deviceId == "phone")
        ) {
            return
        }
        when (envelope) {
            is SyncEnvelope.TupleManifest -> {
                val localTasks = repository.getTasks()
                val toSend = SyncEngine.planForManifest(localTasks, envelope.tuples)
                if (toSend.isNotEmpty()) {
                    val synced = syncStateStore.versions()
                    client.sendTasks(toSend, toSend.associate { it.id to (synced[it.id] ?: 0) })
                }
            }

            is SyncEnvelope.DeltaBundle -> {
                val localTasks = repository.getTasks()
                val plan = SyncEngine.planForDelta(localTasks, envelope.changedTasks, syncStateStore.versions())
                applyTasks(plan.toApplyLocal)
                conflictQueue.enqueue(plan.conflicts)
                syncStateStore.markSynced(plan.ackVersions)
                if (plan.toSend.isNotEmpty()) {
                    val synced = syncStateStore.versions()
                    client.sendTasks(plan.toSend, plan.toSend.associate { it.id to (synced[it.id] ?: 0) })
                }
                client.sendAck(plan.ackVersions, plan.conflicts.map { it.local.id })
            }

            is SyncEnvelope.Ack -> {
                syncStateStore.markSynced(envelope.upToVersion)
            }
        }
    }

    private suspend fun applyTasks(incoming: List<Task>) {
        Log.d(TAG, "applyTasks: ${incoming.size} tasks")
        if (incoming.isEmpty()) return
        repository.update { tasks ->
            val merged = tasks.associateBy(Task::id).toMutableMap()
            incoming.forEach { merged[it.id] = it }
            merged.values.toList()
        }
    }

    companion object {
        private const val TAG = "MariSync"
    }
}
