package com.mari.shared.data.sync

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * End-to-end sync convergence: two devices independently edit tasks; after one plan-and-apply
 * cycle the resulting state must produce no remaining conflicts on a second pass.
 *
 * Conflicts require:
 *   - Both devices changed the same task (localChanged && incomingChanged)
 *   - Different final versions (equal versions → NO_OP per ConflictClassifier)
 *   - Different status values (same status → ADOPT_* by timestamp, not CONFLICT)
 *
 * Phone tasks advance to version 3 with EXECUTING; watch tasks advance to version 2 with
 * COMPLETED. When both edited the same task, versions differ and statuses differ → CONFLICT.
 */
class SyncE2ETest {

    @Test
    fun `1000 independent edits converge with no remaining conflicts after one sync round`() {
        val rng = Random(seed = 1337)
        val taskCount = 1000
        val ids = (1..taskCount).map { "task-$it" }

        val syncedVersions = ids.associateWith { 1 }
        val baseInstant = Instant.parse("2026-04-18T10:00:00Z")

        val phone = ids.map { id ->
            if (rng.nextBoolean()) {
                task(id, version = 3, status = TaskStatus.EXECUTING, modifiedBy = DeviceId.PHONE,
                    updatedAt = baseInstant.plusSeconds(3))
            } else {
                task(id, version = 1, modifiedBy = DeviceId.PHONE, updatedAt = baseInstant)
            }
        }
        val watch = ids.map { id ->
            if (rng.nextBoolean()) {
                task(id, version = 2, status = TaskStatus.COMPLETED, modifiedBy = DeviceId.WATCH,
                    updatedAt = baseInstant.plusSeconds(2))
            } else {
                task(id, version = 1, modifiedBy = DeviceId.WATCH, updatedAt = baseInstant)
            }
        }

        val plan = SyncEngine.planForDelta(phone, watch, syncedVersions)

        // Genuine conflicts must exist (phone v3 EXECUTING vs watch v2 COMPLETED)
        assertThat(plan.conflicts).isNotEmpty()

        // After applying the plan, a second round produces zero conflicts
        val phoneAfterApply = applyPlan(phone, plan)
        val updatedSyncedVersions = syncedVersions + plan.ackVersions

        val rePlan = SyncEngine.planForDelta(
            phoneAfterApply,
            plan.toSend,
            updatedSyncedVersions,
        )

        assertWithMessage("Conflicts not resolved after one sync round: ${rePlan.conflicts}")
            .that(rePlan.conflicts)
            .isEmpty()
    }

    @Test
    fun `all non-conflicting phone edits reach watch via toSend`() {
        val rng = Random(seed = 99)
        val ids = (1..500).map { "t$it" }
        val syncedVersions = ids.associateWith { 1 }
        val baseInstant = Instant.parse("2026-04-18T10:00:00Z")

        // Phone edits all tasks to v3; watch edits half of them to v2 with a different status
        val phone = ids.map { id ->
            task(id, version = 3, status = TaskStatus.EXECUTING, modifiedBy = DeviceId.PHONE,
                updatedAt = baseInstant.plusSeconds(3))
        }
        val watch = ids.map { id ->
            if (rng.nextBoolean()) {
                task(id, version = 2, status = TaskStatus.COMPLETED, modifiedBy = DeviceId.WATCH,
                    updatedAt = baseInstant.plusSeconds(2))
            } else {
                task(id, version = 1, modifiedBy = DeviceId.WATCH, updatedAt = baseInstant)
            }
        }

        val plan = SyncEngine.planForDelta(phone, watch, syncedVersions)

        val conflictIds = plan.conflicts.map { it.local.id }.toSet()
        val sentIds = plan.toSend.map { it.id }.toSet()

        // Every non-conflicting phone edit (where watch didn't edit) must be in toSend
        val watchEditedIds = watch.filter { it.version > 1 }.map { it.id }.toSet()
        val expectedToSend = ids.toSet() - conflictIds - watchEditedIds
        assertThat(sentIds).isEqualTo(expectedToSend)
    }

    @Test
    fun `all non-conflicting watch edits reach phone via toApplyLocal`() {
        val rng = Random(seed = 77)
        val ids = (1..500).map { "t$it" }
        val syncedVersions = ids.associateWith { 1 }
        val baseInstant = Instant.parse("2026-04-18T10:00:00Z")

        // Watch edits all tasks to v2; phone edits half of them to v3 with a different status
        val phone = ids.map { id ->
            if (rng.nextBoolean()) {
                task(id, version = 3, status = TaskStatus.EXECUTING, modifiedBy = DeviceId.PHONE,
                    updatedAt = baseInstant.plusSeconds(3))
            } else {
                task(id, version = 1, modifiedBy = DeviceId.PHONE, updatedAt = baseInstant)
            }
        }
        val watch = ids.map { id ->
            task(id, version = 2, status = TaskStatus.COMPLETED, modifiedBy = DeviceId.WATCH,
                updatedAt = baseInstant.plusSeconds(2))
        }

        val plan = SyncEngine.planForDelta(phone, watch, syncedVersions)

        val conflictIds = plan.conflicts.map { it.local.id }.toSet()
        val appliedIds = plan.toApplyLocal.map { it.id }.toSet()

        // Tasks where only watch edited (phone stayed at v1) must all be in toApplyLocal
        val phoneEditedIds = phone.filter { it.version > 1 }.map { it.id }.toSet()
        val expectedApplied = ids.toSet() - conflictIds - phoneEditedIds
        assertThat(appliedIds).isEqualTo(expectedApplied)
    }

    @Test
    fun `idempotent - running planForDelta twice on same state yields empty plan`() {
        val ids = (1..200).map { "t$it" }
        val tasks = ids.map { id -> task(id, version = 3, modifiedBy = DeviceId.PHONE) }
        val syncedVersions = tasks.associate { it.id to it.version }

        val plan = SyncEngine.planForDelta(tasks, tasks, syncedVersions)

        assertThat(plan.toSend).isEmpty()
        assertThat(plan.toApplyLocal).isEmpty()
        assertThat(plan.conflicts).isEmpty()
    }

    // — helpers —

    private fun applyPlan(local: List<Task>, plan: SyncPlan): List<Task> {
        val overrides = plan.toApplyLocal.associateBy(Task::id)
        return local.map { task -> overrides[task.id] ?: task }
    }

    private fun task(
        id: String,
        version: Int,
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        modifiedBy: DeviceId = DeviceId.PHONE,
        updatedAt: Instant = Instant.parse("2026-04-18T10:00:00Z").plusSeconds(version.toLong()),
    ): Task = Task(
        id = id,
        description = "Task $id",
        status = status,
        createdAt = Instant.parse("2026-04-18T09:00:00Z"),
        updatedAt = updatedAt,
        executionStartedAt = null,
        deletedAt = null,
        version = version,
        lastModifiedBy = modifiedBy,
    )
}
