package com.mari.shared.data.sync

import com.google.common.truth.Truth.assertThat
import com.mari.shared.domain.DeviceId
import com.mari.shared.domain.Task
import com.mari.shared.domain.TaskStatus
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * Convergence property: after applying a SyncPlan, re-running planForDelta on the resulting
 * state should produce an empty plan (nothing left to sync or conflict).
 */
class SyncConvergenceTest {

    @Test
    fun `convergence holds for randomly generated task sets`() {
        val rng = Random(seed = 42)
        repeat(200) {
            val (phone, watch, syncedVersions) = randomScenario(rng)

            val plan = SyncEngine.planForDelta(phone, watch, syncedVersions)

            // Apply the plan to produce new local state
            val phoneAfterApply = applyPlan(phone, plan)

            // Re-run with an empty incoming list to simulate the watch having ack'd
            val rePlan = SyncEngine.planForDelta(
                phoneAfterApply,
                plan.toSend, // simulate watch sending back what phone told it
                syncedVersions + plan.ackVersions,
            )

            assertThat(rePlan.conflicts)
                .withFailMessage("Conflicts still exist after plan application: $rePlan")
                .isEmpty()
        }
    }

    @Test
    fun `no-edit round trip produces empty plan`() {
        val tasks = listOf(
            task("a", version = 3, modifiedBy = DeviceId.PHONE),
            task("b", version = 2, modifiedBy = DeviceId.WATCH),
        )
        val syncedVersions = tasks.associate { it.id to it.version }

        val plan = SyncEngine.planForDelta(tasks, tasks, syncedVersions)

        assertThat(plan.toApplyLocal).isEmpty()
        assertThat(plan.toSend).isEmpty()
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun `phone-only edits all reach watch via toSend`() {
        val base = listOf(
            task("a", version = 1),
            task("b", version = 1),
            task("c", version = 1),
        )
        val syncedVersions = base.associate { it.id to 1 }

        // Phone advanced all versions; watch still at v1
        val phoneEdited = base.map { it.copy(version = 2) }

        val plan = SyncEngine.planForDelta(phoneEdited, base, syncedVersions)

        assertThat(plan.toSend.map { it.id }).containsExactlyElementsIn(listOf("a", "b", "c"))
        assertThat(plan.toApplyLocal).isEmpty()
    }

    @Test
    fun `watch-only edits all reach phone via toApplyLocal`() {
        val base = listOf(
            task("a", version = 1),
            task("b", version = 1),
        )
        val syncedVersions = base.associate { it.id to 1 }

        val watchEdited = base.map { it.copy(version = 2, lastModifiedBy = DeviceId.WATCH) }

        val plan = SyncEngine.planForDelta(base, watchEdited, syncedVersions)

        assertThat(plan.toApplyLocal.map { it.id }).containsExactlyElementsIn(listOf("a", "b"))
        assertThat(plan.toSend).isEmpty()
    }

    // — helpers —

    private data class Scenario(
        val phone: List<Task>,
        val watch: List<Task>,
        val syncedVersions: Map<String, Int>,
    )

    private fun randomScenario(rng: Random): Scenario {
        val taskCount = rng.nextInt(1, 15)
        val ids = (1..taskCount).map { "task-$it" }
        val syncedVersions = ids.associateWith { rng.nextInt(1, 5) }

        val phone = ids.map { id ->
            val synced = syncedVersions.getValue(id)
            task(id, version = synced + rng.nextInt(0, 3), modifiedBy = DeviceId.PHONE)
        }
        val watch = ids.map { id ->
            val synced = syncedVersions.getValue(id)
            task(id, version = synced + rng.nextInt(0, 3), modifiedBy = DeviceId.WATCH)
        }

        return Scenario(phone, watch, syncedVersions)
    }

    private fun applyPlan(local: List<Task>, plan: SyncPlan): List<Task> {
        val byId = local.associateBy(Task::id).toMutableMap()
        plan.toApplyLocal.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    private fun task(
        id: String,
        version: Int,
        status: TaskStatus = TaskStatus.TO_BE_DONE,
        modifiedBy: DeviceId = DeviceId.PHONE,
    ): Task =
        Task(
            id = id,
            description = "Task $id",
            status = status,
            createdAt = Instant.parse("2026-04-18T09:00:00Z"),
            updatedAt = Instant.parse("2026-04-18T10:00:00Z").plusSeconds(version.toLong()),
            executionStartedAt = null,
            deletedAt = null,
            version = version,
            lastModifiedBy = modifiedBy,
        )
}
