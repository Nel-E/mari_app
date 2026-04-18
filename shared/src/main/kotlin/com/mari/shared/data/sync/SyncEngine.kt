package com.mari.shared.data.sync

import com.mari.shared.domain.Task

data class SyncConflict(
    val local: Task,
    val incoming: Task,
    val decision: ConflictDecision,
)

data class SyncPlan(
    val toSend: List<Task> = emptyList(),
    val toApplyLocal: List<Task> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
    val ackVersions: Map<String, Int> = emptyMap(),
)

object SyncEngine {

    fun planForManifest(
        localTasks: List<Task>,
        remoteTuples: List<TaskTuple>,
    ): List<Task> {
        val remoteById = remoteTuples.associateBy(TaskTuple::id)
        return localTasks.filter { task ->
            val remote = remoteById[task.id]
            remote == null || task.version > remote.version
        }
    }

    fun planForDelta(
        localTasks: List<Task>,
        incomingTasks: List<Task>,
        lastSyncedVersionByTask: Map<String, Int>,
    ): SyncPlan {
        val localById = localTasks.associateBy(Task::id)
        val send = mutableListOf<Task>()
        val apply = mutableListOf<Task>()
        val conflicts = mutableListOf<SyncConflict>()
        val ack = mutableMapOf<String, Int>()

        for (incoming in incomingTasks) {
            val local = localById[incoming.id]
            when (ConflictClassifier.classify(local, incoming, lastSyncedVersionByTask[incoming.id])) {
                ConflictDecision.NO_OP -> ack[incoming.id] = incoming.version
                ConflictDecision.ADOPT_INCOMING -> {
                    apply += incoming
                    ack[incoming.id] = incoming.version
                }
                ConflictDecision.ADOPT_LOCAL -> local?.let(send::add)
                ConflictDecision.CONFLICT,
                ConflictDecision.MERGE_DELETE_EDIT,
                -> {
                    if (local != null) {
                        conflicts += SyncConflict(local = local, incoming = incoming, decision = ConflictClassifier.classify(local, incoming, lastSyncedVersionByTask[incoming.id]))
                    }
                }
            }
        }

        return SyncPlan(
            toSend = send.distinctBy(Task::id),
            toApplyLocal = apply.distinctBy(Task::id),
            conflicts = conflicts,
            ackVersions = ack,
        )
    }
}
