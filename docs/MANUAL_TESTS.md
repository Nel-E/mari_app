# Manual Test Checklist

Tests that require a physical device, emulator, or hardware sensor and cannot be automated in CI.

---

## Prerequisites

- Physical Android device (API 26+) or emulator with Play Services
- Wear OS watch paired via Wear OS companion app (for sync tests)
- App installed in debug variant: `com.mari.app.debug`

---

## 1. SAF Folder Picker

| # | Step | Expected |
|---|------|----------|
| 1.1 | Fresh install → launch app | Folder-picker prompt appears |
| 1.2 | Grant a folder via picker | App loads (or shows empty task list) |
| 1.3 | Revoke permission from Settings → Apps → Mari | `StorageError` banner shown; tasks still visible read-only |
| 1.4 | Re-grant via Settings screen | Tasks reload from disk |
| 1.5 | Move phone to a different user profile | Permission revoked gracefully, no crash |

---

## 2. Shake-to-Pick

| # | Step | Expected |
|---|------|----------|
| 2.1 | Add 2+ TO_BE_DONE tasks; shake device | Pick dialog appears with one task name |
| 2.2 | Tap "Re-roll" in pick dialog | Different task shown (if >1 available) |
| 2.3 | Confirm pick → task starts | Task moves to EXECUTING; reminder scheduled |
| 2.4 | Shake with zero eligible tasks | No dialog shown |
| 2.5 | Shake within 1.5s of previous shake | No second dialog (debounce) |
| 2.6 | Shake while another task is executing | Conflict dialog shown with existing and incoming |
| 2.7 | Conflict: "Finish & Switch" | Existing task → COMPLETED; incoming → EXECUTING |
| 2.8 | Conflict: "Pause & Switch" | Existing task → PAUSED; incoming → EXECUTING |

---

## 3. Voice Input

| # | Step | Expected |
|---|------|----------|
| 3.1 | Tap mic icon on Add Task screen | System speech recognizer launches |
| 3.2 | Speak task description clearly | Text populated in input field |
| 3.3 | Cancel speech recognizer | Input field unchanged |
| 3.4 | Speak only whitespace / silence | Input field unchanged (Empty result) |
| 3.5 | No speech app installed | Error message shown gracefully |

---

## 4. Reminders

| # | Step | Expected |
|---|------|----------|
| 4.1 | Start a task; wait 30 min | Reminder notification appears |
| 4.2 | Tap notification | App opens to executing task screen |
| 4.3 | Complete the task | Reminder notification cancelled |
| 4.4 | Force-stop app; reboot device | Reminder rescheduled on boot via BootReceiver |
| 4.5 | Revoke `POST_NOTIFICATIONS` permission | No crash; silent failure logged |

---

## 5. Weekly Backup Rotation

| # | Step | Expected |
|---|------|----------|
| 5.1 | Use app for 7+ days (or advance device clock) | Second backup file created alongside `tasks.cbor` |
| 5.2 | Corrupt `tasks.cbor` manually | App recovers from `.bak` file; banner shown |
| 5.3 | Corrupt both `tasks.cbor` and `.bak` | Graceful error screen; no crash |

---

## 6. Wear OS Sync

| # | Step | Expected |
|---|------|----------|
| 6.1 | Add task on phone | Task appears on watch within ~10s |
| 6.2 | Complete task on watch | Task marked COMPLETED on phone |
| 6.3 | Edit same task on phone and watch simultaneously | Conflict resolved by last-write-wins (higher version wins) |
| 6.4 | Disconnect watch; add tasks on phone; reconnect | Tasks sync on reconnect |
| 6.5 | Uninstall phone app; reinstall | Watch reconnects and syncs |

---

## 7. Accessibility

| # | Step | Expected |
|---|------|----------|
| 7.1 | Enable TalkBack; navigate task list | All items announced with meaningful content descriptions |
| 7.2 | Increase font size to largest | No text truncation or overlap |
| 7.3 | Enable high contrast / dark theme | All text remains legible |
| 7.4 | Navigate with switch access (single-switch scanning) | All interactive elements reachable |

---

## 8. Edge Cases

| # | Step | Expected |
|---|------|----------|
| 8.1 | Add 100+ tasks | List scrolls smoothly; no OOM |
| 8.2 | Rotate device mid-dialog | Dialog state preserved |
| 8.3 | Kill app from recent apps during shake | No crash on next launch |
| 8.4 | Run in split-screen mode | UI adapts; no layout overflow |
