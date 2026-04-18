package com.mari.wear.sync

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mari.shared.data.repository.TaskRepository
import com.mari.shared.data.sync.SyncEnvelope
import com.mari.shared.data.sync.TaskTuple
import com.mari.shared.domain.Clock
import com.mari.wear.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class WatchSyncClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dataClient by lazy { Wearable.getDataClient(context) }

    fun start() {
        scope.launch {
            repository.observeTasks()
                .map { tasks -> tasks.map(TaskTuple::from) }
                .distinctUntilChanged()
                .collect { tuples ->
                    sendEnvelope(
                        SyncEnvelope.TupleManifest(
                            sentAtUtc = clock.nowUtc(),
                            deviceId = "watch",
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
                deviceId = "watch",
                fromVersionByTask = fromVersions,
                changedTasks = tasks,
            ),
        )
    }

    fun sendAck(upToVersion: Map<String, Int>, conflicts: List<String> = emptyList()) {
        sendEnvelope(
            SyncEnvelope.Ack(
                sentAtUtc = clock.nowUtc(),
                deviceId = "watch",
                upToVersion = upToVersion,
                conflicts = conflicts,
            ),
        )
    }

    private fun sendEnvelope(envelope: SyncEnvelope) {
        val request = PutDataMapRequest.create("/mari/sync/v1/${System.currentTimeMillis()}").apply {
            dataMap.putByteArray("payload", SyncEnvelope.encode(envelope))
            dataMap.putInt("schemaVersion", envelope.schemaVersion)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
    }
}
