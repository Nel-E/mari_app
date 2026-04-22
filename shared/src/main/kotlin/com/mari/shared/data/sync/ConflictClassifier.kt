package com.mari.shared.data.sync

import com.mari.shared.domain.Task

enum class ConflictDecision {
    NO_OP,
    ADOPT_INCOMING,
    ADOPT_LOCAL,
    CONFLICT,
    MERGE_DELETE_EDIT,
}

object ConflictClassifier {

    fun classify(
        local: Task?,
        incoming: Task?,
        lastSyncedVersion: Int?,
    ): ConflictDecision {
        if (local == null && incoming == null) return ConflictDecision.NO_OP
        if (local == null) return ConflictDecision.ADOPT_INCOMING
        if (incoming == null) return ConflictDecision.ADOPT_LOCAL
        if (local.version == incoming.version) return ConflictDecision.NO_OP

        val baseline = lastSyncedVersion
        if (baseline != null) {
            val localChanged = local.version > baseline
            val incomingChanged = incoming.version > baseline

            if (!localChanged && incomingChanged) return ConflictDecision.ADOPT_INCOMING
            if (localChanged && !incomingChanged) return ConflictDecision.ADOPT_LOCAL
        }

        val localDeleted = local.deletedAt != null
        val incomingDeleted = incoming.deletedAt != null
        if (localDeleted.xor(incomingDeleted)) return ConflictDecision.MERGE_DELETE_EDIT

        if (
            local.name == incoming.name &&
            local.description == incoming.description &&
            local.status == incoming.status &&
            local.deletedAt == incoming.deletedAt &&
            local.executionStartedAt == incoming.executionStartedAt &&
            local.dueAt == incoming.dueAt &&
            local.dueKind == incoming.dueKind &&
            local.deadlineReminders == incoming.deadlineReminders &&
            local.colorHex == incoming.colorHex
        ) {
            return if (incoming.updatedAt >= local.updatedAt) {
                ConflictDecision.ADOPT_INCOMING
            } else {
                ConflictDecision.ADOPT_LOCAL
            }
        }

        if (local.status == incoming.status) {
            return if (incoming.updatedAt >= local.updatedAt) {
                ConflictDecision.ADOPT_INCOMING
            } else {
                ConflictDecision.ADOPT_LOCAL
            }
        }

        return ConflictDecision.CONFLICT
    }
}
