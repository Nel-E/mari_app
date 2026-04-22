# Phase 2 — Task Properties (Deadline, Reminders, Color)

**Depends on:** Phase 0 (fields + resolver), Phase 1 (name validation).
**Unblocks:** Phase 3 (daily nudge references due-date-aware active task).

## Goal

Let the user set:
- A **task name** (required, unique — Phase 1 enforces).
- **Creation date** and **last modification date** — displayed read-only.
- A **task deadline / due date** using presets: specific day (optional time), this week, next week, this month, next month, pick month+year.
- **Deadline reminders** — 0 to N reminders at user-defined offsets from `dueAt`. Up to **4 reusable templates** saved in settings and selectable when creating/editing a task.
- A **color** for the task (`colorHex`). **Required** when a deadline is set. Rendered on the task row/card in `AllTasksScreen`.

Phone owns deadline editing in this phase. The watch must be updated enough to create valid named tasks and to display the new primary `name` field, but watch-side deadline editing can remain read-only/deferred.

## Rationale

Gives the user structured deadline workflows, configurable reminder cadence, and visual salience for deadline-bearing tasks.

## Files (new/modified)

### New
- `app/src/main/kotlin/com/mari/app/reminders/DeadlineReminderScheduler.kt`
- `app/src/main/kotlin/com/mari/app/reminders/DeadlineReminderReceiver.kt`
- `app/src/main/kotlin/com/mari/app/reminders/BootRescheduler.kt`
- `app/src/main/kotlin/com/mari/app/ui/components/DueDatePicker.kt`
- `app/src/main/kotlin/com/mari/app/ui/components/ColorSwatchPicker.kt`
- `app/src/main/kotlin/com/mari/app/ui/components/ReminderTemplatePicker.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/ReminderTemplatesSection.kt`
- Tests for each new pure component's logic.

### Modified
- `app/src/main/kotlin/com/mari/app/settings/PhoneSettings.kt` — add `deadlineReminderTemplates: List<DeadlineReminder>`.
- `app/src/main/kotlin/com/mari/app/settings/SettingsRepository.kt` — persist templates.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskScreen.kt` — add required name field plus deadline / color / reminder rows.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskViewModel.kt` — handle new fields.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/EditTaskSheet.kt` — same.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/AllTasksScreen.kt` — render `colorHex` as row accent and use `name` as primary label.
- `app/src/main/kotlin/com/mari/app/reminders/BootReceiver.kt` — delegate to `BootRescheduler`.
- `shared/src/main/kotlin/com/mari/shared/domain/ExecutionRules.kt` — accept new task fields and add a shared metadata-edit helper.
- `shared/src/main/kotlin/com/mari/shared/domain/TaskListing.kt` — sort/filter/search on `name` first, not `description` alone.
- Selected phone + wear UI surfaces — switch primary display text from `description` to `name`, keep `description` as optional notes.
- `wear/src/main/kotlin/com/mari/wear/ui/screens/add/AddTaskScreen.kt` / `AddTaskViewModel.kt` — collect required `name`, optional notes.

## Detailed Steps

### 1. Settings — reminder templates

```kotlin
data class PhoneSettings(
    /* …existing… */
    val deadlineReminderTemplates: List<DeadlineReminder> = listOf(
        DeadlineReminder(offsetSeconds = -86_400,   label = "1 day before"),
        DeadlineReminder(offsetSeconds = -3_600,    label = "1 hour before"),
        DeadlineReminder(offsetSeconds = 0,         label = "At due time"),
        DeadlineReminder(offsetSeconds = -10_800,   label = "3 hours before"),
    ),
)
```

Max 4 templates enforced in `SettingsRepository`.

### 2. Reminder scheduler

`DeadlineReminderScheduler` mirrors `AlarmReminderScheduler`:

```kotlin
interface DeadlineReminderScheduler {
    fun schedule(task: Task, reminders: List<DeadlineReminder>)
    fun cancel(taskId: String)
}
```

- Each reminder has its own `PendingIntent` keyed by `(taskId, offsetSeconds)` hash.
- Trigger time = `task.dueAt + reminder.offsetSeconds`.
- Past-trigger times are dropped silently at schedule time (do not notify retroactively).
- Same fallback as existing scheduler: `setAndAllowWhileIdle` when exact alarm permission is missing.

### 3. Reminder receiver

`DeadlineReminderReceiver` posts a notification via the existing `ReminderNotifier` channel with:
- Title: the reminder `label` or formatted offset.
- Body: `"<task.name> — due <localized due time>"`.
- Tap → deep link to the task detail.

Respect quiet hours via existing `QuietHours` helper.

### 4. Task creation wiring

`ExecutionRules.createTask(...)` gains new defaulted params:

```kotlin
fun createTask(
    name: String,
    description: String = "",
    clock: Clock,
    deviceId: DeviceId,
    dueAt: Instant? = null,
    dueKind: DueKind? = null,
    deadlineReminders: List<DeadlineReminder> = emptyList(),
    colorHex: String? = null,
): Task
```

Also add a shared metadata edit path so every rename / notes edit / deadline edit / color change updates `updatedAt`, increments `version`, and stamps `lastModifiedBy` consistently from one place instead of open-coding `task.copy(...)` in multiple ViewModels.

### 5. UI — AddTaskScreen / EditTaskSheet

Layout additions:

- **Name field** — required, single-line, primary task label used in lists, reminders, and conflict UI.
- **Notes field** — optional multiline field backed by existing `description`.
- **Created / Modified rows** in edit mode — read-only.

- **Deadline row** — tap → `DueDatePicker` bottom sheet:
  - Radio buttons: Specific day / This week / Next week / This month / Next month / Pick month & year.
  - For "Specific day": Material3 `DatePicker` + optional `TimePicker`.
  - For "Pick month & year": two spinners / number pickers.
  - Output: `DueKind` + resolved `dueAt`.
- **Reminders row** — tap → `ReminderTemplatePicker`:
  - Checkboxes over the 4 configured templates.
  - "Custom…" entry opens a small offset editor (days/hours/minutes, before/after).
- **Color row** — tap → `ColorSwatchPicker`:
  - A curated palette of ~12 colors + a "custom hex" option.
  - **Save button disabled** when deadline is set but color is null. Error text: "Choose a color for tasks with deadlines".

### 6. AllTasksScreen row rendering

- Leading color strip (4dp wide) painted with `colorHex` when present.
- Task row title uses `task.name`; notes/description stay secondary if shown.
- Deadline chip showing relative time: "Due in 2h", "Due tomorrow", "Overdue 3h", with `ui.theme` red for overdue.
- Sort option "Due soonest first" appended to `TaskSortMode`.
- Search and A-Z sort operate on `name` first; notes can remain searchable as a secondary match.

### 7. Settings — ReminderTemplatesSection

New section in `SettingsScreen.kt`:
- Four rows, each a template. Tap → edit `(offsetSeconds, label)`.
- Cannot delete below 0 templates but can empty a label.

### 8. Boot re-arm

Introduce `BootRescheduler` and move boot-time scheduling logic out of `BootReceiver`. It must:
- Load the persisted SAF grant and task file directly from disk instead of assuming the in-memory repository has already been initialized.
- Re-arm existing execution reminders (current feature) and new deadline reminders for each non-completed task with future `dueAt`.
- Be reusable by Phase 3 when daily nudge is added.

## Tests (RED before GREEN)

1. `DueDatePickerStateTest` — pure state machine around preset selection.
2. `DeadlineReminderSchedulerTest` (fakes `AlarmManager`):
   - Schedule 3 reminders → 3 pending intents.
   - `schedule` then `cancel` → 0 pending intents.
   - Past-trigger reminders dropped.
   - Exact-alarm denied → falls back to `setAndAllowWhileIdle`.
3. `AddTaskViewModelDeadlineTest`:
   - Pick deadline without color → save blocked.
   - Pick deadline + color + 2 reminders → saved with those fields, scheduler called.
   - Edit task to remove deadline → scheduler cancel called.
4. `TaskListingNameMigrationTest`:
   - Search by `name` returns the task even when notes differ.
   - A-Z sort orders by `name`, not notes.
5. `AllTasksScreenRenderTest` (Compose test or screenshot):
   - Row with `colorHex = "#FF8A65"` has the strip painted.
   - Overdue task chip is red.
6. `BootReschedulerTest`:
   - Cold-start boot path with persisted SAF grant loads tasks from disk and re-arms future deadline reminders.
7. Migration regression: `SchemaMigrationsV1ToV2Test` must still pass.

## Validation Gate

- [ ] All tests green.
- [ ] Manual: create task with specific-day + 2 reminders + color → notifications fire at the right times (verified with short offsets like `-60s`).
- [ ] Manual: create task with `ThisMonth` preset near end of month → due time is last day 23:59 local.
- [ ] Manual: edit task to change due date → previous alarms cancelled, new alarms set (verify with `adb shell dumpsys alarm | grep mari`).
- [ ] Manual: reboot device → alarms re-armed from BootReceiver.
- [ ] Manual: create task with deadline but no color → save button disabled, error visible.
- [ ] Settings: edit 4th template's offset → reflected in picker on next task creation.
- [ ] Watch manual test: watch add-task flow collects `name` and syncs a valid task; watch list/main surfaces show `name` as the primary label.

## Exit Criteria

Full deadline workflow visible, testable, and persistent across reboots.
Commit sequence (small conceptual commits preferred):
1. `feat(tasks): add deadline, color, and reminder fields to Task domain`
2. `feat(reminders): schedule/cancel deadline reminders via AlarmManager`
3. `feat(ui): deadline picker, color swatch, and reminder template picker`
4. `feat(settings): configurable deadline reminder templates (up to 4)`
5. `feat(ui): render task color strip and due-date chips in AllTasksScreen`

---

## Implementation Progress

- [ ] `not implemented` `PhoneSettings` extended with `deadlineReminderTemplates` (4 defaults)
- [ ] `not implemented` `SettingsRepository` persists templates; enforces max-4 limit
- [ ] `not implemented` `DeadlineReminderScheduler` interface + `AlarmManager` implementation
- [ ] `not implemented` `DeadlineReminderReceiver` posts notification with quiet-hours check
- [ ] `not implemented` `BootRescheduler` loads persisted tasks at boot and re-arms reminders without relying on repository init
- [ ] `not implemented` shared metadata edit helper added so rename / notes / deadline / color edits all stamp version metadata consistently
- [ ] `not implemented` `ExecutionRules.createTask` accepts new deadline/color params
- [ ] `not implemented` `DueDatePicker` bottom sheet composable (all 6 presets)
- [ ] `not implemented` `ColorSwatchPicker` composable (~12 swatches + custom hex)
- [ ] `not implemented` `ReminderTemplatePicker` composable (checkboxes + custom offset editor)
- [ ] `not implemented` `AddTaskScreen` / `AddTaskViewModel` wired with name + notes + deadline + color + reminders
- [ ] `not implemented` `EditTaskSheet` wired with deadline + color + reminders (cancel old alarms on change)
- [ ] `not implemented` task list/search/reminder surfaces switched to `name` as the primary label
- [ ] `not implemented` watch add/list/main surfaces updated for required `name` and optional notes
- [ ] `not implemented` `AllTasksScreen` renders color strip and due-date chip per row
- [ ] `not implemented` `ReminderTemplatesSection` in `SettingsScreen`
- [ ] `not implemented` `BootReceiver` delegates boot-time scheduling to `BootRescheduler`
- [ ] `not implemented` `DeadlineReminderSchedulerTest` written and green
- [ ] `not implemented` `AddTaskViewModelDeadlineTest` written and green
- [ ] `not implemented` `TaskListingNameMigrationTest` written and green
- [ ] `not implemented` `BootReschedulerTest` written and green
- [ ] `not implemented` `AllTasksScreenRenderTest` written and green (color strip, overdue chip)

## Functional Requirements / Key Principles

- A task with a deadline must have a `colorHex`; the save button is disabled and an error is shown until a color is chosen.
- `task.name` is the primary label everywhere user-facing; `description` remains optional notes storage.
- Deadline reminders are keyed by `(taskId, offsetSeconds)` so editing a task cancels all previous alarms and schedules the new set atomically.
- Past-trigger reminder times (resolved `dueAt + offsetSeconds` < now at schedule time) are dropped silently; no retroactive notifications fire.
- Quiet hours are honored for deadline notifications using the same `QuietHours` helper as other reminders.
- Removing a deadline from a task cancels all associated `DeadlineReminderScheduler` alarms immediately.
- The `colorHex` field is optional when no deadline is set; the UI enforces the requirement only when `dueAt != null`.
- Boot-time rescheduling must load persisted tasks directly from disk; it cannot rely on repository state already being hydrated in memory.
- On device reboot, deadline alarms for non-completed tasks with future `dueAt` are re-armed; no alarm is lost after reboot.
- Deadline presets resolve via `DueDateResolver`; the resolved `Instant` is stored in `dueAt` alongside the original `DueKind` for display-label reconstruction.
