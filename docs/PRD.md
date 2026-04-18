# Mari Task Manager — Product Requirements Document

**Version:** 1.0
**Date:** 2026-04-17
**Status:** Draft (ready for implementation planning)

---

## 1. Overview

Mari is a single-user task manager for Android phones and Galaxy Watch (Wear OS). It combines fast voice capture, a "shake-to-pick" engagement mechanic, and user-owned persistent storage that survives app uninstall and reinstall.

### 1.1 Goals

- Capture tasks quickly via voice.
- Help the user choose what to do next via a physical shake gesture.
- Keep a single, user-visible task with status `executing` at any time.
- Persist all task data to a user-chosen folder so data survives uninstall / reinstall / updates.
- Provide a standalone Wear OS companion that works without the phone.

### 1.2 Non-Goals (v1)

- Multi-user / accounts / cloud sync.
- Sub-tasks, tags, priorities, due dates.
- Collaboration or sharing.
- Undelete UI (soft-delete is wired but hidden).

---

## 2. Platforms & Targets

| Platform | Min SDK | Target SDK | Form Factor |
|---|---|---|---|
| Android phone | 26 (Android 8.0) | 35 | Phones only (no tablet-specific UI) |
| Wear OS      | 30 (Wear OS 3) | 35 | Galaxy Watch (round display, no rotating crown) |

Language: Kotlin. UI: Jetpack Compose (phone) + Compose for Wear OS (watch).

---

## 3. Data Model

### 3.1 Task

| Field | Type | Notes |
|---|---|---|
| `id` | UUID string | Generated at creation. |
| `description` | String | Free text (from voice or edit). |
| `status` | Enum | `to_be_done`, `paused`, `executing`, `queued`, `completed`, `discarded`. |
| `createdAt` | ISO-8601 UTC | Immutable. |
| `updatedAt` | ISO-8601 UTC | Updated on any field change. |
| `executionStartedAt` | ISO-8601 UTC / null | Set when status→`executing`. Cleared on reset / terminal status change. |
| `deletedAt` | ISO-8601 UTC / null | Soft-delete timestamp. Filters, search, and shake-pool exclude when non-null. |
| `version` | Integer | Incremented on every mutation. Used with `lastModifiedBy` for conflict detection. |
| `lastModifiedBy` | Enum | `phone` or `watch`. |

### 3.2 Status Rules

- Initial status on new task creation: `to_be_done`.
- All transitions are legal **except**: setting a task to `executing` when another task is already `executing`. See §6.3.
- Terminal-ish statuses (`completed`, `discarded`) do not auto-clear `executionStartedAt`; it is cleared explicitly on reset or when moving out of `executing`.
- On first install, seed one test task titled **"New Task"** so the main screen is never empty. Also auto-create a "New Task" seed if the user deletes every remaining non-deleted task.

### 3.3 Soft-Delete

- Delete action sets `deletedAt`; record is never physically removed.
- "Show deleted" toggle on All Tasks screen is implemented but hidden behind a feature flag in v1.

### 3.4 File Format

Single JSON file, top-level object:

```json
{
  "schemaVersion": 1,
  "tasks": [ { …Task… } ],
  "settings": { "deviceId": "phone|watch" }
}
```

Settings are NOT synced between phone and watch and live in a separate per-device settings store (see §8).

---

## 4. Storage

### 4.1 Approach: Storage Access Framework (SAF)

On first launch, the phone app prompts the user to pick a folder via `ACTION_OPEN_DOCUMENT_TREE`. Persist the URI with `takePersistableUriPermission`. This grant survives reinstall *if the folder is not inside the app's private directory*.

### 4.2 Canonical File

- Filename: `mari_tasks.json` in the user-selected folder.
- **Atomic writes**: write to `mari_tasks.json.tmp`, fsync, rename to `mari_tasks.json`.
- **Rolling backups**: `mari_tasks.json.bak` (previous version) rewritten on every successful save.
- **Weekly backup**: on the first save each Sunday, copy to `backups/mari_tasks.YYYY-WW.json`. Retain last 8 weekly backups; delete older ones.

### 4.3 Failure Handling

- **Corrupt main file** → load `.bak`. Never start from empty when a file previously existed; surface an error banner instead.
- **Missing SAF grant** (revoked, folder deleted) → re-prompt the user. App is read-only in-memory until resolved. No silent crash.
- All I/O errors surface a user-visible message with a retry path.

### 4.4 Watch Cache

The watch keeps a local cache file in app-private storage (scoped; lost on uninstall is acceptable because the phone is canonical). On sync it receives the full canonical state or a delta from the phone.

---

## 5. Sync (Phone ↔ Watch)

### 5.1 Transport

- Google Play Services **Wearable Data Layer API** (`DataClient`). Handles offline queueing.
- Phone is source of truth for task data; watch holds a cache.

### 5.2 Protocol

- On connect, both sides exchange `(taskId, version, updatedAt, lastModifiedBy)` tuples.
- Deltas are sent as full task records.
- After successful merge, both sides persist.

### 5.3 Conflict Resolution

Per-task rules:

| Situation | Behavior |
|---|---|
| Same `version` on both sides | No-op. |
| One side newer, other unchanged since last sync | Adopt newer. |
| Both sides edited since last sync (divergent versions) | **Show conflict dialog on phone** (watch defers). |
| Delete vs edit | **Show conflict dialog on phone.** |
| Both sides changed status to the same value | Take newer `updatedAt`, no prompt. |
| Both sides changed status to different values | **Show conflict dialog on phone.** |

### 5.4 Conflict Dialog

Shown on the phone when the user opens the app after a divergent sync. Displays the diff between phone version and watch version, and offers:

1. **Keep phone version** (discard watch changes).
2. **Keep watch version** (overwrite phone).
3. **Keep both as two tasks** (watch version becomes a new task with new id).
4. **Cancel** — leave unresolved; prompt again on next launch.

If multiple conflicts are queued, resolve one at a time.

### 5.5 Standalone Watch Mode

If the watch has never been paired, or the phone is unreachable:

- Watch operates from its local cache (or seeded empty state).
- Supported: add task (voice), view tasks, mark complete, shake-to-pick, change status, delete task.
- Not supported (watch-limited by design): task edit, search, filter, conflict resolution, Settings for weekly backup cadence.
- All watch-side changes queue for sync; propagate to phone on next connection.

---

## 6. Android Phone App

### 6.1 Main Screen

Primary entry. Always shows **Add Task**, **View Tasks**. Plus **exactly one** of:

- **Mark executing complete** — when a task has status `executing`. Tapping it opens a bottom sheet:
  - *Pause* → status `paused`, clear `executionStartedAt`.
  - *Complete* → status `completed`, clear `executionStartedAt`.
  - *Reset* → status `to_be_done`, clear `executionStartedAt`.
  - *Cancel* → close sheet.
- **Shake to pick a task** — when no task has status `executing`.

If the task list is empty (all deleted), the system auto-creates a "New Task" so this rule always holds.

### 6.2 Add Task Flow

1. Launch Android system speech recognizer (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`).
2. On success: create task with the spoken text, status `to_be_done`. Save. Return to Main.
3. On failure or cancel: show **Retry / Cancel** dialog. Retry re-launches the recognizer. Cancel returns to Main with no task created.

### 6.3 Select-for-Execution Semantics

"Selecting a task to execute" and "changing status to `executing`" are the **same action**. If another task is already `executing` when the user does this:

Show dialog: **"Task X is currently executing. Finish X, pause X, or cancel?"**

- *Finish X* → X → `completed` (`executionStartedAt` cleared); new task → `executing`.
- *Pause X* → X → `paused` (`executionStartedAt` cleared); new task → `executing`.
- *Cancel* → no change.

### 6.4 Shake-to-Pick

- App listens to accelerometer only while **app is open** (no background service).
- Trigger threshold: configurable magnitude + duration (see §9).
- Debounce: 1500 ms between triggers.
- Selection pool:
  - If any task has status `queued` → pick only from `queued`.
  - Else → pick from `to_be_done` ∪ `paused` ∪ `queued`.
  - Always exclude `completed`, `discarded`, and soft-deleted.
- Result UI: **"Picked: <description>. Start? / Re-roll? / Cancel"**
  - *Start* → apply §6.3 (may prompt about existing executing task).
  - *Re-roll* → pick again.
  - *Cancel* → return to Main.
- Play configured shake sound + vibration (per §8).

### 6.5 All Tasks Screen

- Scrollable list; default sort: executing → queued → paused → to_be_done → completed → discarded; within each group by `updatedAt` desc.
- Additional sort options: *Default*, *A–Z*, *Z–A*, *Created (newest)*, *Created (oldest)*, *Modified (newest)*, *Modified (oldest)*.
- **Filter** by status (multi-select chips).
- **Search** by keyword (case-insensitive substring match on `description`).
- Per-task actions (tap or long-press):
  - **Edit** — all fields editable including status (status change obeys §6.3).
  - **Select to execute** — §6.3.
  - **Delete** — confirmation dialog. Confirm button is disabled for 2 seconds after dialog opens to prevent accidental double-tap. Soft-delete only.
  - **Mark complete** — confirmation dialog.
  - **Change status** — sheet with all six statuses.
- **Gear icon** → Settings.

---

## 7. Wear OS App (Galaxy Watch)

### 7.1 Main Screen

Same three-button logic as phone (§6.1), styled for round Galaxy Watch display, no rotating-crown assumption; use touch-scroll and button taps.

### 7.2 Add Task

System speech recognition on watch. Same retry/cancel flow as phone.

### 7.3 All Tasks Screen

- Scrollable list of tasks.
- Tap a task for actions:
  - Select to execute (§6.3 logic; watch also prompts on executing-conflict).
  - Delete (2-second disabled confirm, soft-delete).
  - Mark complete (confirmation).
  - Change status (all six).
- **No edit. No search. No filter.** (Intentional; surface in phone app.)
- Gear icon → Watch Settings.

### 7.4 Shake-to-Pick

Identical semantics to phone (§6.4), only while the watch app is open.

---

## 8. Settings

### 8.1 Per-Device

Settings are **NOT synced** between phone and watch because available sounds and vibration patterns differ.

### 8.2 Settings Screen (Phone)

- **Shake strength** — slider 10–30 m/s², default 15.
- **Shake duration** — slider 100–1000 ms, default 300.
- **Shake sound** — picker of device notification sounds (incl. "None").
- **Shake vibration** — on/off.
- **Execution reminder**
  - Enable/disable.
  - Frequency in **hours and minutes** (min 1 min, max 24 hr).
  - Reminder sound + vibration (uses device sounds).
  - **Quiet hours** — e.g. 08:00–18:00; reminders outside window suppressed (respect device DND in all cases).
- **Storage folder** — shows current SAF folder; "Change folder" re-launches `ACTION_OPEN_DOCUMENT_TREE`.
- **Weekly backup** — info-only (shows last/next backup time).

### 8.3 Settings Screen (Watch)

Same fields as phone **except**: no storage folder (cache only), no weekly backup info. Sound/vibration pickers use watch-available options.

---

## 9. Shake Detection

- Listener: `SensorManager` with `TYPE_LINEAR_ACCELERATION`.
- Trigger when magnitude ≥ configured strength for ≥ configured duration.
- Debounce: ignore triggers within 1500 ms of previous.
- Only active while app foregrounded.

---

## 10. Execution Reminders

- Scheduled when a task enters `executing`.
- Cleared automatically when the task leaves `executing` (complete / pause / reset / status change / delete).
- Scheduling strategy:
  - For intervals ≥ 15 min: `WorkManager` periodic work.
  - For intervals < 15 min: `AlarmManager` (`setExactAndAllowWhileIdle` or inexact depending on `SCHEDULE_EXACT_ALARM` grant).
- Each reminder posts a notification with the configured sound/vibration; respects quiet hours and DND.
- If a reminder is already scheduled and the user changes frequency, cancel existing and reschedule.

---

## 11. Voice Input

- Android: `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, language from device locale.
- Wear OS: same intent (system provides on-watch voice UI).
- Requires `RECORD_AUDIO` permission (request on first Add Task).
- Cancel or empty → user sees **Retry / Cancel** dialog.

---

## 12. Permissions

| Permission | Required for | When requested |
|---|---|---|
| `RECORD_AUDIO` | Voice task input | First Add Task use |
| `POST_NOTIFICATIONS` | Execution reminders, shake feedback (Android 13+) | First use of feature |
| `VIBRATE` | Shake feedback, reminders | Manifest-only |
| `SCHEDULE_EXACT_ALARM` | Sub-15-min reminder precision (Android 12+) | Only if user sets <15 min reminder |

No location, no background sensors, no body sensors.

---

## 13. Non-Functional Requirements

- **Offline-first.** No feature requires network except voice recognition (if on-device unavailable).
- **Data loss prevention.** Atomic writes, rolling backups, weekly snapshots, re-prompt on missing grant, load-from-.bak on corruption.
- **Accessibility.** TalkBack labels on all actionable elements; large-text safe layouts; sufficient contrast for status colors.
- **Crash resilience.** Process death during voice capture or sync must not corrupt the JSON file.

---

## 14. Open Questions (to resolve during implementation)

- Exact visual treatment of conflict diff dialog.
- Whether to surface a "sync status" indicator on the main screen.
- Exact color tokens for each status (design task).

---

## 15. Glossary

- **SAF** — Storage Access Framework, Android API allowing user-granted folder access.
- **Canonical** — authoritative data source (phone file).
- **Cache** — non-authoritative local copy (watch file).
- **Soft-delete** — mark as deleted via `deletedAt` without removing the record.
