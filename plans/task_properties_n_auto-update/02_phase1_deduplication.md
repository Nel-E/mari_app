# Phase 1 — Task Name Deduplication

**Depends on:** Phase 0 (`Task.name` field exists).
**Unblocks:** Phase 2 (deadline UI uses the validated name).

## Goal

Reject creation or rename of a task when another non-deleted task already has the same name (case-insensitive, trimmed). Surface a targeted inline error in the UI and keep the form open for user correction. Handle cross-device sync collisions gracefully.

## Rationale

User requirement: "tasks must have different names. If user is trying to create a task with the same name, stop and ask for a name that differentiates the task."

Case-insensitive matching prevents `"Call mom"` vs `"call mom"` ambiguity. Trimming prevents trailing-space fakes.

## Files (new/modified)

### New
- `shared/src/main/kotlin/com/mari/shared/domain/DuplicateTaskNameException.kt`
- `shared/src/test/kotlin/com/mari/shared/domain/TaskValidationUniqueNameTest.kt`
- `app/src/test/kotlin/com/mari/app/ui/screens/add/AddTaskViewModelDedupTest.kt`
- `app/src/test/kotlin/com/mari/app/ui/screens/tasks/AllTasksViewModelDedupTest.kt`
- `wear/src/test/kotlin/com/mari/wear/ui/screens/add/AddTaskViewModelDedupTest.kt`
- `shared/src/test/kotlin/com/mari/shared/data/sync/TaskNameDedupSyncTest.kt`

### Modified
- `shared/src/main/kotlin/com/mari/shared/domain/TaskValidation.kt` — add `validateUniqueName`.
- `shared/src/main/kotlin/com/mari/shared/data/sync/TaskNameDeduplicator.kt` (new helper in shared sync layer).
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskViewModel.kt` — wire unique-name check before save.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/AllTasksViewModel.kt` — enforce unique-name check on rename/edit save.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/EditTaskSheet.kt` — surface inline name error.
- `wear/src/main/kotlin/com/mari/wear/ui/screens/add/AddTaskViewModel.kt` — enforce unique-name check on watch task creation.
- `app/src/main/kotlin/com/mari/app/sync/PhoneSyncService.kt` — run dedup pass after sync merge, before persist.
- `wear/src/main/kotlin/com/mari/wear/sync/WatchSyncService.kt` — same.

## Detailed Steps

### 1. Pure validation function

```kotlin
object TaskValidation {
    private const val MAX_NAME_LENGTH = 80

    fun validateName(raw: String): Result<String> {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> Result.failure(IllegalArgumentException("Task name must not be blank"))
            trimmed.length > MAX_NAME_LENGTH ->
                Result.failure(IllegalArgumentException("Task name must be $MAX_NAME_LENGTH characters or fewer"))
            else -> Result.success(trimmed)
        }
    }

    fun validateUniqueName(
        name: String,
        existing: List<Task>,
        excludingId: String? = null,
    ): Result<String> {
        val target = name.trim().lowercase()
        val clash = existing.firstOrNull { t ->
            t.deletedAt == null && t.id != excludingId && t.name.trim().lowercase() == target
        }
        return if (clash != null) {
            Result.failure(DuplicateTaskNameException(clash.id, clash.name))
        } else {
            Result.success(name.trim())
        }
    }
}
```

### 2. Typed exception

```kotlin
class DuplicateTaskNameException(val existingTaskId: String, val existingTaskName: String) :
    IllegalStateException("A task named '$existingTaskName' already exists")
```

### 3. Wire into AddTaskViewModel

- Before the final `repository.update { }`, read the current task list once and call `validateUniqueName`.
- On failure, set `uiState.nameError = "A task named '<X>' already exists"` and **do not** clear the input.
- `isSaving` returns to false; the dialog remains open.

### 4. Wire into the current edit-save flow

- The current codebase saves edits through `AllTasksViewModel`, not a dedicated edit ViewModel.
- Pass `excludingId = currentTask.id` so renaming a task to its own current name is a no-op success.
- Surface the validation error inline in `EditTaskSheet`; do not close the sheet on failure.

### 5. Cross-device sync dedup pass

When phone and watch create tasks offline, there is a legitimate window where two devices create the same normalized name with different task IDs. Strategy:

- Add a shared helper `TaskNameDeduplicator.resolveCollisions(tasks, clock, receivingDeviceId)` and call it from the sync apply path on both phone and watch after the normal ID-based merge:
  - Group non-deleted tasks by `name.trim().lowercase()`.
  - For each group with `> 1` entry, keep the earliest `createdAt` as-is and rename the others to `"<name> (2)"`, `"<name> (3)"`, etc. (incrementing until unique within the merged set).
  - Each auto-rename must also increment `version`, because sync propagation in this app is version-based.
  - Use the receiving device id (`PHONE` or `WATCH`) as `lastModifiedBy`; do not invent a new device id solely for dedup.
  - Return a small report so the caller can log each rename at `INFO`.

## Tests (RED before GREEN)

1. `TaskValidationUniqueNameTest`:
   - Unique name in empty list → success.
   - Same name differing only in case → failure.
   - Same name differing only in trailing whitespace → failure.
   - Duplicate but soft-deleted → success (the tombstone does not block).
   - Duplicate excluded by `excludingId` → success (self-rename no-op).
2. `AddTaskViewModelDedupTest`:
   - Save with clashing name emits `nameError`, does not call repository.
   - Save with unique name calls repository exactly once, emits `saved = true`.
3. `AllTasksViewModelDedupTest`:
   - Rename to own name → success (no error, no-op write).
   - Rename to another task's name → error.
4. `wear AddTaskViewModelDedupTest`:
   - Save with clashing name emits inline error, does not write.
   - Save with unique name succeeds.
5. `TaskNameDedupSyncTest`:
   - Two devices create task "Gym" → merged file has "Gym" and "Gym (2)".
   - Three devices create "Gym" → "Gym", "Gym (2)", "Gym (3)".
   - Auto-rename preserves `createdAt`, bumps `updatedAt`, increments `version`, and sets `lastModifiedBy` to the receiving device.

## Validation Gate

- [ ] All new tests green.
- [ ] Manual device test: create "Buy milk" → create second "buy milk" → error banner, original saved.
- [ ] Manual device test: rename "Buy milk" to "Buy MILK" → error.
- [ ] Manual device test: rename "Buy milk" to "Buy milk" (self) → accepted.
- [ ] Simulated cross-device sync (phone + watch create the same name offline) → dedup pass renames one, nothing lost.
- [ ] Watch manual test: create duplicate name from the watch UI → inline error, no write.

## Exit Criteria

Duplicates blocked at all task-creation/write surfaces and during sync merge, tests green, log entry on auto-rename is visible.
Commit: `feat(tasks): enforce unique task names with case-insensitive dedup`.

---

## Implementation Progress

- [ ] `not implemented` `DuplicateTaskNameException` typed exception
- [ ] `not implemented` `TaskValidation.validateName` (blank check, max-length)
- [ ] `not implemented` `TaskValidation.validateUniqueName` (case-insensitive, trimmed, excludingId support)
- [ ] `not implemented` `AddTaskViewModel` wired with unique-name check before save
- [ ] `not implemented` `AllTasksViewModel` / `EditTaskSheet` rename flow wired with unique-name check (`excludingId = currentTask.id`)
- [ ] `not implemented` watch `AddTaskViewModel` wired with unique-name check before save
- [ ] `not implemented` shared sync dedup pass (suffix "(2)", "(3)", etc.; increments `version`; `lastModifiedBy = receivingDeviceId`)
- [ ] `not implemented` `TaskValidationUniqueNameTest` written and green
- [ ] `not implemented` `AddTaskViewModelDedupTest` written and green
- [ ] `not implemented` `AllTasksViewModelDedupTest` written and green
- [ ] `not implemented` watch `AddTaskViewModelDedupTest` written and green
- [ ] `not implemented` `TaskNameDedupSyncTest` written and green

## Functional Requirements / Key Principles

- Task name uniqueness is enforced case-insensitively and after trimming; `"Call mom"` and `"call mom "` are treated as the same name.
- Soft-deleted tasks (non-null `deletedAt`) are excluded from the uniqueness check; a deleted name can be reused.
- Renaming a task to its own current name (case-preserved or not) is always accepted without error.
- Uniqueness is enforced on every current write path: phone add, phone edit/rename, and watch add.
- On a cross-device collision, the earliest `createdAt` task keeps its original name; all others are suffixed `" (N)"` in ascending order.
- Auto-renamed tasks during sync merge increment `version`, bump `updatedAt`, and set `lastModifiedBy` to the receiving device; the rename is logged at INFO level.
- The UI error is inline (not a toast); the form remains open and the name field retains the conflicting input for user correction.
- Repository uniqueness check reads the current task list once per save attempt; there is no background polling or optimistic locking.
