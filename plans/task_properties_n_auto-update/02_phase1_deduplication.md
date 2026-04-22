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
- `app/src/test/kotlin/com/mari/app/ui/screens/tasks/EditTaskViewModelDedupTest.kt` (if not already present, create)

### Modified
- `shared/src/main/kotlin/com/mari/shared/domain/TaskValidation.kt` — add `validateUniqueName`.
- `shared/src/main/kotlin/com/mari/shared/data/repository/TaskRepository.kt` — dedup pass on import-time file merges.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskViewModel.kt` — wire unique-name check before save.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/EditTaskSheet.kt` — wire unique-name check on rename.

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

### 4. Wire into EditTaskSheet rename flow

- Pass `excludingId = currentTask.id` so renaming a task to its own current name is a no-op success.

### 5. Cross-device sync dedup pass

When `FileTaskRepository` merges an imported `TaskFile` (e.g. watch → phone handoff), there is a legitimate window where two devices create the same name offline. Strategy:

- In `FileTaskRepository.merge(local, remote)`, after union, run a dedup pass:
  - Group non-deleted tasks by `name.trim().lowercase()`.
  - For each group with `> 1` entry, keep the earliest `createdAt` as-is, rename the others to `"<name> (2)"`, `"<name> (3)"`, etc. (incrementing until unique within the merged set), updating `updatedAt` and `lastModifiedBy = DeviceId.SYSTEM` (add this enum value if needed).
  - Log each rename at `INFO` so the user can see in logs what happened.

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
3. `EditTaskViewModelDedupTest`:
   - Rename to own name → success (no error, no-op write).
   - Rename to another task's name → error.
4. `FileTaskRepositoryDedupMergeTest`:
   - Two devices create task "Gym" → merged file has "Gym" and "Gym (2)".
   - Three devices create "Gym" → "Gym", "Gym (2)", "Gym (3)".
   - Rename preserves `createdAt`, bumps `updatedAt`, sets `lastModifiedBy = SYSTEM`.

## Validation Gate

- [ ] All new tests green.
- [ ] Manual device test: create "Buy milk" → create second "buy milk" → error banner, original saved.
- [ ] Manual device test: rename "Buy milk" to "Buy MILK" → error.
- [ ] Manual device test: rename "Buy milk" to "Buy milk" (self) → accepted.
- [ ] Simulated cross-device merge (drop two conflicting files in `tasks.json` + `tasks.json.incoming`) → dedup pass renames one, nothing lost.

## Exit Criteria

Duplicates blocked at UI and at file-merge, tests green, log entry on auto-rename is visible.
Commit: `feat(tasks): enforce unique task names with case-insensitive dedup`.

---

## Implementation Progress

- [ ] `not implemented` `DuplicateTaskNameException` typed exception
- [ ] `not implemented` `TaskValidation.validateName` (blank check, max-length)
- [ ] `not implemented` `TaskValidation.validateUniqueName` (case-insensitive, trimmed, excludingId support)
- [ ] `not implemented` `AddTaskViewModel` wired with unique-name check before save
- [ ] `not implemented` `EditTaskSheet` rename flow wired with unique-name check (`excludingId = currentTask.id`)
- [ ] `not implemented` `FileTaskRepository.merge` dedup pass (suffix "(2)", "(3)", etc.; `lastModifiedBy = SYSTEM`)
- [ ] `not implemented` `TaskValidationUniqueNameTest` written and green
- [ ] `not implemented` `AddTaskViewModelDedupTest` written and green
- [ ] `not implemented` `EditTaskViewModelDedupTest` written and green
- [ ] `not implemented` `FileTaskRepositoryDedupMergeTest` written and green

## Functional Requirements / Key Principles

- Task name uniqueness is enforced case-insensitively and after trimming; `"Call mom"` and `"call mom "` are treated as the same name.
- Soft-deleted tasks (non-null `deletedAt`) are excluded from the uniqueness check; a deleted name can be reused.
- Renaming a task to its own current name (case-preserved or not) is always accepted without error.
- On a cross-device merge collision, the earliest `createdAt` task keeps its original name; all others are suffixed `" (N)"` in ascending order.
- Auto-renamed tasks during merge have `lastModifiedBy = DeviceId.SYSTEM` and a bumped `updatedAt`; the rename is logged at INFO level.
- The UI error is inline (not a toast); the form remains open and the name field retains the conflicting input for user correction.
- Repository uniqueness check reads the current task list once per save attempt; there is no background polling or optimistic locking.
