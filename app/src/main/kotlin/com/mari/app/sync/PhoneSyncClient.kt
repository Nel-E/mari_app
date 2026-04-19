package com.mari.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mari.app.di.ApplicationScope
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.sync.SyncEnvelope
import com.mari.shared.data.sync.TaskTuple
import com.mari.shared.domain.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class PhoneSyncClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun start() {
        Log.d(TAG, "start: beginning task observation")
        scope.launch {
            repository.observeTasks()
                .map { tasks -> tasks.map(TaskTuple::from) }
                .distinctUntilChanged()
                .collect { tuples ->
                    Log.d(TAG, "start: task list changed, sending TupleManifest with ${tuples.size} tasks")
                    sendEnvelope(
                        SyncEnvelope.TupleManifest(
                            sentAtUtc = clock.nowUtc(),
                            deviceId = "phone",
                            tuples = tuples,
                        ),
                    )
                }
        }
    }

    fun sendTasks(tasks: List<com.mari.shared.domain.Task>, fromVersions: Map<String, Int> = emptyMap()) {
        sendEnvelope(
            SyncEnvelope.DeltaBundle(
                sentAtUtc = clock.nowUtc(),
                deviceId = "phone",
                fromVersionByTask = fromVersions,
                changedTasks = tasks,
            ),
        )
    }

    fun sendAck(upToVersion: Map<String, Int>, conflicts: List<String> = emptyList()) {
        sendEnvelope(
            SyncEnvelope.Ack(
                sentAtUtc = clock.nowUtc(),
                deviceId = "phone",
                upToVersion = upToVersion,
                conflicts = conflicts,
            ),
        )
    }

    private fun sendEnvelope(envelope: SyncEnvelope) {
        val type = envelope::class.simpleName
        Log.d(TAG, "sendEnvelope: $type")
        val request = PutDataMapRequest.create("/mari/sync/v1/${System.currentTimeMillis()}").apply {
            dataMap.putByteArray("payload", SyncEnvelope.encode(envelope))
            dataMap.putInt("schemaVersion", envelope.schemaVersion)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
            .addOnSuccessListener { Log.d(TAG, "sendEnvelope: $type delivered ok") }
            .addOnFailureListener { e -> Log.e(TAG, "sendEnvelope: $type failed", e) }
    }

    companion object {
        private const val TAG = "MariSync"
    }
}
