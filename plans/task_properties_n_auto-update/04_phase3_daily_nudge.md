# Phase 3 — Daily Task Reminder

**Depends on:** Phase 2 (active/executing task concept; notification channels reused).
**Unblocks:** nothing — leaf feature.

## Goal

Once a day, at a user-chosen time, the phone posts a notification to nudge the user:
- If there is an **active** (`EXECUTING`) task → prompt the user to continue it or change it.
- If there is **no** active task → prompt to pick one, deep-linked to `AllTasksScreen`.

## Rationale

User requirement: "create a Settings to configure a daily notification time to remind of active task or select a new task."

## Files (new/modified)

### New
- `app/src/main/kotlin/com/mari/app/reminders/DailyNudgeScheduler.kt`
- `app/src/main/kotlin/com/mari/app/reminders/DailyNudgeReceiver.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/DailyNudgeSection.kt`
- Tests for both pure classes.

### Modified
- `app/src/main/kotlin/com/mari/app/settings/PhoneSettings.kt`
- `app/src/main/kotlin/com/mari/app/settings/SettingsRepository.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/mari/app/reminders/BootReceiver.kt`
- `app/src/main/kotlin/com/mari/app/reminders/ReminderNotifier.kt` (new notification factory method)
- `app/src/main/AndroidManifest.xml` — register `DailyNudgeReceiver`.

## Detailed Steps

### 1. Settings additions

```kotlin
data class PhoneSettings(
    /* …existing… */
    val dailyNudgeEnabled: Boolean = false,
    val dailyNudgeHour: Int = 9,
    val dailyNudgeMinute: Int = 0,
)
```

### 2. Scheduler

```kotlin
@Singleton
class DailyNudgeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val clock: Clock,
    private val quietHours: QuietHours,
) {
    fun schedule(hour: Int, minute: Int) { /* setRepeating daily, or setExactAndAllowWhileIdle + re-arm */ }
    fun cancel() { /* cancel PendingIntent */ }
}
```

- Use `AlarmManager.setExactAndAllowWhileIdle` at next occurrence of `(hour, minute)` and re-arm in `DailyNudgeReceiver` after each fire (more reliable on Doze than `setRepeating`).
- If next fire lands inside quiet hours → push to quiet-hours end.
- Fallback to `setAndAllowWhileIdle` when `canScheduleExactAlarms()` is false.

### 3. Receiver logic

```kotlin
class DailyNudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Hilt field injection
        val result = goAsync()
        scope.launch {
            try {
                val tasks = repository.observeAll().first()
                val active = tasks.firstOrNull { it.status == TaskStatus.EXECUTING && it.deletedAt == null }
                if (active != null) {
                    notifier.postActiveTaskNudge(active)
                } else {
                    notifier.postPickTaskNudge()
                }
                scheduler.schedule(settings.dailyNudgeHour, settings.dailyNudgeMinute)  // re-arm
            } finally { result.finish() }
        }
    }
}
```

### 4. Notifications

Two styles on the existing `ReminderNotifier` channel:

- **Active task nudge** — title `"Still on <name>?"`, actions: `Continue` (dismisses), `Change` (opens `AllTasksScreen`), `Complete` (marks done via a `NudgeActionReceiver`).
- **Pick task nudge** — title `"Pick a task for today"`, tap opens `AllTasksScreen`.

### 5. Settings UI (DailyNudgeSection)

- Toggle: "Daily task reminder".
- Time picker row (Material3 `TimePicker` dialog): shows `HH:MM`.
- Info text: "You'll be nudged once a day to continue or pick a task. Quiet hours are honored."
- Changing toggle or time → `scheduler.schedule(...)` or `scheduler.cancel()` immediately.

### 6. Boot re-arm

`BootReceiver.onReceive` reads settings and re-arms if `dailyNudgeEnabled`.

## Tests (RED before GREEN)

1. `DailyNudgeSchedulerTest` (fake `AlarmManager` + fixed `Clock`):
   - `schedule(9, 0)` at 2026-04-22T08:00 local → trigger at 2026-04-22T09:00 local.
   - `schedule(9, 0)` at 2026-04-22T09:30 local → trigger at 2026-04-23T09:00 local.
   - Trigger inside quiet hours `[22:00, 07:00]` with `schedule(6, 30)` → bumped to 07:00 local.
   - `cancel()` removes the pending intent.
   - Exact-alarm denied → falls back to `setAndAllowWhileIdle`.
2. `DailyNudgeReceiverTest`:
   - EXECUTING task present → `postActiveTaskNudge` called, `postPickTaskNudge` not called.
   - No EXECUTING task → only `postPickTaskNudge` called.
   - After fire, scheduler is re-armed exactly once.
3. `SettingsViewModelDailyNudgeTest`:
   - Toggle on with default time → `scheduler.schedule` called.
   - Toggle off → `scheduler.cancel` called.
   - Time change while enabled → cancel + re-schedule.

## Validation Gate

- [ ] All tests green.
- [ ] Manual: enable daily nudge at a time 2 min in the future. Wait. Notification fires.
- [ ] Manual with active task: notification shows "Still on <name>?" with Continue/Change/Complete actions. All three paths work.
- [ ] Manual without active task: notification shows "Pick a task for today", tap → lands on AllTasksScreen.
- [ ] Manual: set nudge inside quiet hours → fires at quiet-hours end instead.
- [ ] Manual: reboot → next nudge still fires at the configured time.
- [ ] `adb shell dumpsys alarm | grep mari` shows exactly one daily nudge alarm, no duplicates.

## Exit Criteria

Daily nudge fires at the right time, respects quiet hours, survives reboot, correct branching between active and no-active-task states.
Commit: `feat(reminders): add configurable daily task nudge with quiet-hours support`.

---

## Implementation Progress

- [ ] `not implemented` `PhoneSettings` extended with `dailyNudgeEnabled`, `dailyNudgeHour`, `dailyNudgeMinute`
- [ ] `not implemented` `DailyNudgeScheduler` (setExactAndAllowWhileIdle + fallback; quiet-hours push)
- [ ] `not implemented` `DailyNudgeReceiver` fires notification, branches on EXECUTING task, re-arms alarm
- [ ] `not implemented` `ReminderNotifier` extended with `postActiveTaskNudge` and `postPickTaskNudge`
- [ ] `not implemented` `DailyNudgeSection` composable in `SettingsScreen`
- [ ] `not implemented` `SettingsViewModel` wired: toggle on/off, time change → schedule/cancel
- [ ] `not implemented` `BootReceiver` re-arms daily nudge on reboot
- [ ] `not implemented` `DailyNudgeSchedulerTest` written and green (future/past time, quiet hours, cancel, fallback)
- [ ] `not implemented` `DailyNudgeReceiverTest` written and green (active vs no-active branching, re-arm)
- [ ] `not implemented` `SettingsViewModelDailyNudgeTest` written and green

## Functional Requirements / Key Principles

- The daily nudge fires at most once per day at the user-configured `(hour, minute)`; `DailyNudgeReceiver` re-arms the next occurrence after each fire.
- If the computed next fire time falls within quiet hours, the alarm is pushed to the end of the quiet-hours window.
- When there is an EXECUTING (non-deleted) task, the notification body names that task and offers Continue / Change / Complete actions. When there is none, it prompts the user to pick a task.
- Quiet hours defined in `PhoneSettings` are the single authoritative source; the nudge scheduler and the existing `AlarmReminderScheduler` both use the same `QuietHours` helper.
- On reboot, `BootReceiver` re-arms the alarm if `dailyNudgeEnabled = true`; no alarm is lost after a restart.
- Toggling the nudge off cancels any pending `PendingIntent` immediately, with no stale alarm left in `AlarmManager`.
- `DailyNudgeScheduler.schedule(h, m)` called while the nudge is already scheduled replaces the pending intent (idempotent re-schedule).
- Exact-alarm permission denial is handled gracefully: falls back to `setAndAllowWhileIdle`, no crash, no missing notification.
