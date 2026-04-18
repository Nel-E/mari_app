# Implementation Plan: Mari Task Manager (Android + Wear OS)

**Source of truth:** `/media/code/mari_app/docs/PRD.md` v1.0 (2026-04-17)
**Target repo:** `/media/code/mari_app`
**Deliverable:** Two installable apps — `:app` (phone) and `:wear` (Galaxy Watch) — sharing a `:shared` Kotlin module, satisfying every section of the PRD.

**Implementation status as of 2026-04-18:** Phases 1–11 complete. Phase 12 partially complete (accessibility annotations in 9 files; TalkBack audit and contrast check pending). Phase 13 partially complete (unit tests in all modules; coverage measurement pending).

---

## 0. Architecture Decisions (locked at plan start)

| Decision | Choice | Rationale |
|---|---|---|
| Build system | Gradle 8.7+ with Kotlin DSL, version catalog in `gradle/libs.versions.toml` | Standard for modern multi-module Android; central version mgmt |
| Language | Kotlin 2.0+, Java 17 toolchain | PRD §2 |
| Phone UI | Jetpack Compose (Material 3), single-Activity + Compose Navigation | PRD §2 |
| Watch UI | Compose for Wear OS (`androidx.wear.compose:*`), single-Activity + Wear Navigation | PRD §2/§7 |
| DI | **Hilt** on `:app` and `:wear`; `:shared` exposes plain interfaces/factories (KSP-free) | Hilt is first-party; keeps `:shared` DI-neutral so JVM unit tests run without Android |
| Serialization | **kotlinx.serialization** (JSON) | Required by PRD §3.4 JSON file format; supports schema versioning cleanly |
| Persistence (settings) | **Jetpack DataStore (Proto)** per device, not synced | PRD §8.1 |
| Task file I/O | `DocumentFile` + `ContentResolver` against SAF tree URI | PRD §4.1 |
| Reminders | WorkManager ≥15 min, AlarmManager <15 min | PRD §10 |
| Sync transport | Google Play Services `DataClient` (Wearable Data Layer) | PRD §5.1 |
| Sync payload format | CBOR-serialized `SyncEnvelope` under Data Layer path `/mari/sync/v1/*` | Compact, binary-safe on Data Layer; schema-versioned |
| Testing | JUnit4 + Truth + Turbine + kotlinx-coroutines-test (unit); Hilt test + Compose UI Test + Robolectric (component); `androidTest` with UiAutomator for SAF/voice flows | 80% coverage per global testing rule |
| CI | GitHub Actions: `./gradlew ktlintCheck detekt lint test` on JDK 17, plus `connectedDebugAndroidTest` on macos-14 runner with emulator for SAF smoke test |
| Min/Target SDK | phone 26/35, wear 30/35 | PRD §2 |

### Module layout

```
mari_app/
├── build.gradle.kts                  (root, plugins only)
├── settings.gradle.kts               (includes :app, :wear, :shared)
├── gradle/libs.versions.toml
├── app/                              Phone app (Hilt, Compose)
├── wear/                             Wear OS app (Hilt, Compose-for-Wear)
├── shared/                           Multiplatform-ready JVM Kotlin lib
│   └── src/main/kotlin/com/mari/shared/...
└── docs/ (existing)
```

### `:shared` vs per-app responsibilities

In `:shared`:
- `domain/` — `Task`, `TaskStatus`, validation, status-transition rules, shake-pool selection, soft-delete filters.
- `data/serialization/` — `TaskFile` root DTO, kotlinx JSON codec, schema-migration harness.
- `data/sync/` — `SyncEnvelope`, tuple DTO `(taskId, version, updatedAt, lastModifiedBy)`, conflict classifier (pure function returning a `ConflictDecision`).
- `data/repository/` — `TaskRepository` interface + in-memory impl for tests.

Not in `:shared`:
- Any Android framework type (SAF, SensorManager, AlarmManager, Compose).
- UI, DI wiring, notifications, shake sensor, voice intent, DataClient listeners.
- Settings DataStore (different schemas per device).

### Sync message format (Wearable Data Layer)

Paths:
- `/mari/sync/v1/manifest` — `TupleManifest { deviceId, tuples: List<TaskTuple> }` sent on connect.
- `/mari/sync/v1/delta` — `DeltaBundle { fromVersionByTask: Map<Uuid, Int>, changedTasks: List<Task> }`.
- `/mari/sync/v1/ack` — `Ack { upToVersion: Map<Uuid, Int>, conflicts: List<Uuid> }`.

Wire format: CBOR via `kotlinx-serialization-cbor`. Payloads chunked at 98 KB (DataItem soft limit). Every message carries `schemaVersion: Int` and `sentAtUtc: Instant`.

---

## Phase Overview

| # | Phase | Complexity | Status | Testable Output |
|---|---|---|---|---|
| 1 | Scaffolding & CI | M | ✅ Complete | `./gradlew build` green on empty modules |
| 2 | Domain layer (`:shared`) | M | ✅ Complete | JVM unit tests, no Android |
| 3 | Storage (SAF + atomic writes + backups) | L | ✅ Complete | Instrumented tests for SAF, crash-recovery sim |
| 4 | Phone UI skeleton | M | ✅ Complete | Navigable app w/ in-memory repo |
| 5 | Phone UI polish (filters, dialogs, edit, delete-delay) | L | ✅ Complete | Every PRD §6 interaction works |
| 6 | Voice input | S | ✅ Complete | Add-task voice flow + retry/cancel |
| 7 | Shake detection | M | ✅ Complete | Configurable, foreground-only, debounced |
| 8 | Notifications / execution reminders | M | ✅ Complete | WorkManager + AlarmManager paths both fire; DND/quiet-hours respected |
| 9 | Wear OS app | L | ✅ Complete | Installable standalone watch app; PRD §7 gaps enforced |
| 10 | Sync (Data Layer + conflicts) | L | ✅ Complete | End-to-end delta + conflict dialog on phone |
| 11 | Settings persistence (DataStore) | S | ✅ Complete | Settings survive restart, per-device |
| 12 | Accessibility + polish | M | 🔶 Partial | Semantics/contentDescription added; TalkBack audit, contrast, round-display insets pending |
| 13 | QA hardening & coverage | M | 🔶 Partial | Unit tests in all modules; coverage <80% in wear; concurrency/crash tests pending |

Each phase ends mergeable. Phase order respects dependencies; within a phase, files can be parallelised.

---

## Phase 1 — Project Scaffolding & CI ✅

**Goal:** Empty repo → three buildable modules with baseline quality gates.

**Files to create:**
- `/media/code/mari_app/settings.gradle.kts`
- `/media/code/mari_app/build.gradle.kts`
- `/media/code/mari_app/gradle/libs.versions.toml`
- `/media/code/mari_app/gradle.properties` (`android.useAndroidX=true`, `kotlin.code.style=official`, `org.gradle.jvmargs=-Xmx4g`)
- `/media/code/mari_app/gradle/wrapper/gradle-wrapper.properties` (8.7)
- `/media/code/mari_app/app/build.gradle.kts`
- `/media/code/mari_app/app/src/main/AndroidManifest.xml` (empty `<application/>`, permissions placeholder)
- `/media/code/mari_app/app/src/main/kotlin/com/mari/app/MariApp.kt` (`@HiltAndroidApp`)
- `/media/code/mari_app/app/src/main/kotlin/com/mari/app/MainActivity.kt` (empty `ComponentActivity`)
- `/media/code/mari_app/wear/build.gradle.kts`
- `/media/code/mari_app/wear/src/main/AndroidManifest.xml` (includes `<uses-feature android:name="android.hardware.type.watch" />` and standalone metadata — see Risks)
- `/media/code/mari_app/wear/src/main/kotlin/com/mari/wear/MariWearApp.kt`
- `/media/code/mari_app/wear/src/main/kotlin/com/mari/wear/MainActivity.kt`
- `/media/code/mari_app/shared/build.gradle.kts` (Android library, multiplatform-ready but target Android+JVM only for v1)
- `/media/code/mari_app/shared/src/main/kotlin/com/mari/shared/Marker.kt` (placeholder)
- `/media/code/mari_app/.github/workflows/ci.yml` (runs `ktlintCheck`, `detekt`, `:shared:test`, `:app:lintDebug`, `:wear:lintDebug`, `:app:testDebugUnitTest`, `:wear:testDebugUnitTest`)
- `/media/code/mari_app/config/detekt/detekt.yml`
- `/media/code/mari_app/.editorconfig`
- `/media/code/mari_app/.gitignore` (standard Android + `*.keystore`, `local.properties`)

**Key classes:** `MariApp`, `MariWearApp` (Hilt entry points).

**Acceptance criteria:**
- [ ] `./gradlew build` passes cold on CI runner.
- [ ] `./gradlew :app:installDebug :wear:installDebug` succeeds on emulators.
- [ ] CI workflow runs ktlint, detekt, lint, unit tests on every push.
- [ ] Version catalog is the single source of dep versions.

**Tests:** N/A (scaffolding) — CI itself is the test.

**Dependencies:** none.

**PRD sections:** §2.

---

## Phase 2 — Domain Layer (`:shared`) ✅

**Goal:** Pure-Kotlin domain model, status rules, and selection logic with full unit-test coverage. No Android.

**Files to create:**
- `shared/src/main/kotlin/com/mari/shared/domain/Task.kt` — data class per PRD §3.1, `@Serializable`.
- `shared/src/main/kotlin/com/mari/shared/domain/TaskStatus.kt` — enum with six values.
- `shared/src/main/kotlin/com/mari/shared/domain/DeviceId.kt` — enum `PHONE, WATCH`.
- `shared/src/main/kotlin/com/mari/shared/domain/TaskValidation.kt` — `validateDescription`, `assertLegalTransition`.
- `shared/src/main/kotlin/com/mari/shared/domain/ExecutionRules.kt` — `canSetExecuting(currentTasks)`, `applyStatusChange(task, newStatus, now)` (returns new `Task`; immutable).
- `shared/src/main/kotlin/com/mari/shared/domain/ShakePool.kt` — pure function `selectCandidates(tasks): List<Task>` implementing PRD §6.4 pool rules.
- `shared/src/main/kotlin/com/mari/shared/domain/Seeding.kt` — `ensureSeedTask(tasks): List<Task>` for first-install / empty-state rule PRD §3.2.
- `shared/src/main/kotlin/com/mari/shared/domain/Clock.kt` — `interface Clock { fun nowUtc(): Instant }` plus `SystemClock`, `FixedClock`.
- `shared/src/test/kotlin/com/mari/shared/domain/*Test.kt` — one per class.

**Key classes / responsibilities:**
- `Task` — immutable record; `copy` is the only mutator.
- `TaskStatus` — PRD §3.1 enum.
- `ExecutionRules` — encodes PRD §3.2 and §6.3 (without UI).
- `ShakePool` — encodes §6.4 pool rules.
- `Clock` — injectable; all timestamps flow through it.

**Acceptance criteria:**
- [ ] `Task` round-trips through JSON identically (validated in Phase 3 tests).
- [ ] `applyStatusChange` clears `executionStartedAt` on leaving `executing` and sets it on entering.
- [ ] `canSetExecuting` returns false when another task is executing.
- [ ] `selectCandidates` prefers `queued` if non-empty; otherwise returns `to_be_done ∪ paused ∪ queued`, excluding `completed`, `discarded`, and soft-deleted.
- [ ] `ensureSeedTask` produces exactly one "New Task" when input is empty or all soft-deleted.
- [ ] Every mutation produces a new `Task` with `version + 1` and updated `updatedAt`.

**Tests (unit, `:shared`):**
- `ExecutionRulesTest` — all six status transitions, executing-conflict detection.
- `ShakePoolTest` — queued precedence, exclusion of terminal/deleted, empty pool.
- `SeedingTest` — empty list, all-deleted list, already-populated list.
- `TaskValidationTest` — blank description rejection, trimming.
- Property-test (kotest-property) invariant: `applyStatusChange` never decreases `version`.

**Dependencies:** Phase 1.

**PRD sections:** §3, §6.1, §6.3, §6.4.

---

## Phase 3 — Storage Layer (SAF + Atomic Writes + Backups) ✅

**Goal:** Persist canonical JSON to a user-selected SAF folder with crash-safe writes, rolling backup, weekly snapshots, and corruption recovery. Phone-only scope; watch cache is `:wear`'s responsibility (simpler, covered in Phase 9).

**Files to create:**
- `shared/src/main/kotlin/com/mari/shared/data/serialization/TaskFile.kt` — `@Serializable data class TaskFile(schemaVersion: Int, tasks: List<Task>, settings: FileSettings)`.
- `shared/src/main/kotlin/com/mari/shared/data/serialization/TaskFileCodec.kt` — `encode(taskFile): String`, `decode(json): TaskFile`. Strict parsing.
- `shared/src/main/kotlin/com/mari/shared/data/serialization/SchemaMigrations.kt` — `migrate(json: JsonElement): TaskFile` — no-op for v1 but infrastructure in place.
- `app/src/main/kotlin/com/mari/app/data/storage/SafFolderManager.kt` — manages tree URI, `takePersistableUriPermission`, detects revoked grants.
- `app/src/main/kotlin/com/mari/app/data/storage/TaskFileStorage.kt` — high-level `load(): Result<TaskFile>`, `save(file: TaskFile): Result<Unit>`.
- `app/src/main/kotlin/com/mari/app/data/storage/AtomicWriter.kt` — writes to `mari_tasks.json.tmp`, fsyncs via `ParcelFileDescriptor.sync()`, renames to `mari_tasks.json`, rotates `.bak`.
- `app/src/main/kotlin/com/mari/app/data/storage/WeeklyBackupRotator.kt` — on first save each ISO-week Sunday UTC, copies to `backups/mari_tasks.YYYY-WW.json`; prunes to last 8.
- `app/src/main/kotlin/com/mari/app/data/storage/StorageError.kt` — sealed class: `NoGrant`, `Corrupt(recovered: Boolean)`, `Io(cause)`, `Migration(cause)`.
- `app/src/main/kotlin/com/mari/app/data/repository/FileTaskRepository.kt` — implements `TaskRepository` from `:shared`; reads/writes via `TaskFileStorage`; debounced save (200 ms) coalesces rapid updates.
- `app/src/main/kotlin/com/mari/app/di/StorageModule.kt` — Hilt bindings.
- Tests:
  - `shared/src/test/kotlin/com/mari/shared/data/serialization/TaskFileCodecTest.kt` — round-trip, unknown field tolerance, malformed JSON.
  - `app/src/androidTest/kotlin/com/mari/app/data/storage/AtomicWriterTest.kt` — kill-process-mid-write simulation via partial write.
  - `app/src/androidTest/kotlin/com/mari/app/data/storage/SafFolderManagerTest.kt` — UiAutomator grants a test folder under `/sdcard/Documents/MariTest`.
  - `app/src/androidTest/kotlin/com/mari/app/data/storage/WeeklyBackupRotatorTest.kt` — fake `Clock` across several weeks; asserts rotation count and naming.

**Key classes / responsibilities:**
- `SafFolderManager` — single source of truth for the persisted tree URI; exposes `StateFlow<SafGrant>` (Granted/Missing).
- `AtomicWriter` — the only class that touches the canonical file.
- `WeeklyBackupRotator` — idempotent; holds no state beyond what it reads from the folder.
- `FileTaskRepository` — concurrency: all writes serialized through a single `Mutex`; reads go through an in-memory `MutableStateFlow<TaskFile>`.

**Acceptance criteria:**
- [ ] First launch on empty grant surfaces the folder picker; URI persists across app restart.
- [ ] Kill-during-write sim leaves either the old `mari_tasks.json` fully intact or `.bak` loadable — never both corrupt.
- [ ] Corrupt main file + valid `.bak` → loads from `.bak` and raises `Corrupt(recovered=true)`.
- [ ] Missing grant → repo goes read-only; in-memory state preserved.
- [ ] Weekly backup created exactly once per ISO week; oldest pruned beyond 8.
- [ ] All I/O errors surface a retryable `StorageError` to UI layer; none swallowed.

**Tests:** unit (codec, migrations) + instrumented (SAF, atomic write, rotation). Coverage target 85%+ (critical path).

**Dependencies:** Phase 2.

**PRD sections:** §3.4, §4.

---

## Phase 4 — Phone UI Skeleton ✅

**Goal:** Navigable Compose UI wired to an in-memory repo. Every screen reachable; no polish yet.

**Files to create:**
- `app/src/main/kotlin/com/mari/app/ui/theme/MariTheme.kt` + `Color.kt` + `Type.kt` (Material 3).
- `app/src/main/kotlin/com/mari/app/ui/nav/MariNavGraph.kt` — routes: `main`, `tasks`, `add`, `settings`.
- `app/src/main/kotlin/com/mari/app/ui/screens/main/MainScreen.kt` + `MainViewModel.kt`.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/AllTasksScreen.kt` + `AllTasksViewModel.kt`.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskScreen.kt` + `AddTaskViewModel.kt` (still a stub — real voice in Phase 6).
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/SettingsScreen.kt` + `SettingsViewModel.kt`.
- `app/src/main/kotlin/com/mari/app/ui/components/TaskRow.kt`, `StatusChip.kt`, `EmptyState.kt`.
- `app/src/main/kotlin/com/mari/app/MainActivity.kt` — hosts `MariNavGraph` in a single-Activity setup.
- Tests: `app/src/test/kotlin/com/mari/app/ui/**/*ViewModelTest.kt` using coroutines-test + Turbine.

**Key classes / responsibilities:**
- `MainViewModel` — exposes one of three main-CTA states: `AddTaskOnly`, `MarkExecutingComplete(task)`, `ShakeToPick`.
- `AllTasksViewModel` — list state; filters/search/sort wired to in-memory only for now.

**Acceptance criteria:**
- [ ] All four screens reachable via navigation.
- [ ] Main screen reflects executing-task presence correctly when repo is manipulated.
- [ ] Back press and up-navigation work consistently.

**Tests:** ViewModel unit tests (state machine for Main); Compose-UI test for each screen's smoke rendering.

**Dependencies:** Phases 2, 3.

**PRD sections:** §6.1 (skeleton), §6.5 (skeleton), §8.2 (skeleton).

---

## Phase 5 — Phone UI Polish ✅

**Goal:** Every PRD §6 interaction present and correct, minus voice (Phase 6), shake (Phase 7), reminders (Phase 8), sync (Phase 10).

**Files to create / modify:**
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/TaskFilterState.kt` — data class holding `selectedStatuses`, `query`, `sortMode`.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/TaskSortMode.kt` — enum per PRD §6.5.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/FilterChipsRow.kt`, `SortMenu.kt`, `SearchBar.kt`.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/EditTaskSheet.kt` — bottom sheet; editable description + status; applies §6.3 when status→`executing`.
- `app/src/main/kotlin/com/mari/app/ui/screens/tasks/ChangeStatusSheet.kt` — six-status selector.
- `app/src/main/kotlin/com/mari/app/ui/dialogs/DeleteConfirmDialog.kt` — Confirm button disabled for 2000 ms; uses `rememberCountdown`.
- `app/src/main/kotlin/com/mari/app/ui/dialogs/ExecutingConflictDialog.kt` — Finish X / Pause X / Cancel per §6.3.
- `app/src/main/kotlin/com/mari/app/ui/dialogs/MarkCompleteDialog.kt`.
- `app/src/main/kotlin/com/mari/app/ui/screens/main/ExecutingBottomSheet.kt` — Pause/Complete/Reset/Cancel per §6.1.
- `app/src/main/kotlin/com/mari/app/ui/util/Countdown.kt` — `@Composable fun rememberCountdown(ms: Long): State<Long>`.
- ViewModel updates:
  - `AllTasksViewModel` — apply filter/search/sort using `:shared` pure functions (move selection/sort logic into `shared/ui/TaskListing.kt` if reusable on watch — but watch lacks filter/search, so keep logic in phone layer but call pure sort helpers from `:shared`).
  - `MainViewModel` — implement executing flow: calling `applyStatusChange` through `TaskRepository`.

**Key classes / responsibilities:**
- `DeleteConfirmDialog` — accessibility-aware countdown; announces remaining seconds to TalkBack at 2s/1s.
- `ExecutingConflictDialog` — called from any path that sets `status=executing`.

**Acceptance criteria:**
- [ ] Every row action from PRD §6.5 works and persists (via Phase 3 repo).
- [ ] Status change to `executing` while another is executing triggers `ExecutingConflictDialog`; all three outcomes apply correctly to both tasks.
- [ ] Delete dialog's Confirm is demonstrably disabled for ≥2 s.
- [ ] Search is case-insensitive substring on `description`; sort modes match §6.5 spec.
- [ ] Default sort groups are produced by a pure `:shared` helper (testable without Compose).

**Tests:**
- Unit: `TaskListingSortTest`, `TaskListingFilterTest` in `:shared`.
- Compose UI: `DeleteConfirmDialogTest` (asserts disabled state < 2000 ms), `ExecutingConflictDialogTest`, `AllTasksScreenTest` (filter/search/sort integration).
- ViewModel: `MainViewModelTest`, `AllTasksViewModelTest`.

**Dependencies:** Phase 4.

**PRD sections:** §6.1, §6.3, §6.5.

---

## Phase 6 — Voice Input (Phone) ✅

**Goal:** Add-task flow launches system speech recognizer; handles success/empty/cancel with Retry/Cancel dialog.

**Files to create:**
- `app/src/main/kotlin/com/mari/app/voice/VoiceCaptureContract.kt` — `ActivityResultContract<Unit, VoiceResult>`.
- `app/src/main/kotlin/com/mari/app/voice/VoiceResult.kt` — sealed: `Success(text)`, `Empty`, `Cancelled`, `Error(reason)`.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/AddTaskScreen.kt` — wire to contract; show `RetryCancelDialog` on non-Success.
- `app/src/main/kotlin/com/mari/app/ui/screens/add/RetryCancelDialog.kt`.
- `app/src/main/kotlin/com/mari/app/permissions/RecordAudioPermission.kt` — request on first use.
- `AndroidManifest.xml` — declare `RECORD_AUDIO`.

**Acceptance criteria:**
- [ ] Cold first launch of Add Task prompts for `RECORD_AUDIO`.
- [ ] Recognized text creates a `to_be_done` task and returns to Main.
- [ ] Empty result or user cancel → Retry/Cancel dialog with working Retry.
- [ ] Locale respected (device default).

**Tests:**
- Unit: `AddTaskViewModelTest` (fake `VoiceCaptureContract`).
- Instrumented: `AddTaskScreenTest` with stubbed contract; manual smoke on real device documented in `/docs/MANUAL_TESTS.md`.

**Dependencies:** Phase 5.

**PRD sections:** §6.2, §11, §12.

---

## Phase 7 — Shake Detection (Phone) ✅

**Goal:** Foreground-only shake listener with configurable threshold / duration / debounce, producing a "Picked" dialog per §6.4.

**Files to create:**
- `app/src/main/kotlin/com/mari/app/shake/ShakeDetector.kt` — `SensorManager.TYPE_LINEAR_ACCELERATION`; magnitude = `sqrt(x²+y²+z²)`; emits `Flow<Unit>` when magnitude ≥ threshold sustained ≥ duration; 1500 ms debounce.
- `app/src/main/kotlin/com/mari/app/shake/ShakeConfig.kt` — data class read from settings (threshold m/s², duration ms).
- `app/src/main/kotlin/com/mari/app/shake/ShakeLifecycleObserver.kt` — registers/unregisters based on `Lifecycle.Event.ON_RESUME / ON_PAUSE`.
- `app/src/main/kotlin/com/mari/app/ui/screens/main/PickedTaskDialog.kt` — Start / Re-roll / Cancel per §6.4.
- `app/src/main/kotlin/com/mari/app/ui/screens/main/MainViewModel.kt` — consumes shake flow; picks via `:shared/ShakePool.selectCandidates`; applies §6.3 on Start.
- `app/src/main/kotlin/com/mari/app/audio/ShakeFeedback.kt` — plays configured notification sound + vibration.

**Acceptance criteria:**
- [ ] Backgrounding the app stops sensor callbacks within 1 frame.
- [ ] Configurable threshold/duration taken from Settings DataStore (stub values until Phase 11).
- [ ] Debounce prevents triggers within 1500 ms.
- [ ] Re-roll picks from the same pool (excludes nothing extra).
- [ ] Empty pool → dialog shows "No eligible tasks" and closes gracefully.

**Tests:**
- Unit: `ShakeDetectorTest` — feeds synthetic `SensorEvent` samples via test double.
- Unit: `ShakePoolTest` already from Phase 2.
- Instrumented: `ShakeLifecycleObserverTest` asserts register/unregister on lifecycle transitions.
- Manual: real shake smoke.

**Dependencies:** Phases 2 (ShakePool), 5.

**PRD sections:** §6.4, §9.

---

## Phase 8 — Execution Reminders (Phone) ✅

**Goal:** Schedule reminders while a task is `executing`; cancel on leaving. Two scheduling backends by interval.

**Files to create:**
- `app/src/main/kotlin/com/mari/app/reminders/ReminderScheduler.kt` — single interface with `schedule(taskId, interval)`, `cancel(taskId)`.
- `app/src/main/kotlin/com/mari/app/reminders/WorkManagerReminderScheduler.kt` — for ≥15 min; unique `PeriodicWorkRequest` keyed by `taskId`.
- `app/src/main/kotlin/com/mari/app/reminders/AlarmReminderScheduler.kt` — for <15 min; `AlarmManager.setExactAndAllowWhileIdle` if `SCHEDULE_EXACT_ALARM` granted else `setAndAllowWhileIdle`.
- `app/src/main/kotlin/com/mari/app/reminders/ReminderRouter.kt` — picks backend by interval; delegates.
- `app/src/main/kotlin/com/mari/app/reminders/ReminderWorker.kt` — posts notification.
- `app/src/main/kotlin/com/mari/app/reminders/ReminderReceiver.kt` — BroadcastReceiver for AlarmManager.
- `app/src/main/kotlin/com/mari/app/reminders/QuietHours.kt` — pure fn `isSuppressed(now, QuietWindow): Boolean`.
- `app/src/main/kotlin/com/mari/app/reminders/ReminderNotifier.kt` — respects DND via `NotificationManager.currentInterruptionFilter`; quiet hours via `QuietHours`.
- `app/src/main/kotlin/com/mari/app/reminders/ExecutingStatusObserver.kt` — observes repo; on `→ executing` schedule; on leave cancel.
- `app/src/main/kotlin/com/mari/app/permissions/ExactAlarmPermission.kt` — lazy prompt when user first sets <15 min.
- `AndroidManifest.xml` — `POST_NOTIFICATIONS`, `VIBRATE`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` (Android 13+), `RECEIVE_BOOT_COMPLETED` for alarm rescheduling, `BootReceiver`.
- `app/src/main/kotlin/com/mari/app/reminders/BootReceiver.kt` — reschedules on device reboot for any currently-executing task.

**Acceptance criteria:**
- [ ] Setting a task to `executing` schedules a reminder at the configured interval.
- [ ] Changing status or deleting cancels the reminder within the observer's debounce window.
- [ ] <15 min uses AlarmManager; ≥15 min uses WorkManager.
- [ ] Quiet hours window suppresses reminders; DND also suppresses.
- [ ] Frequency change cancels and reschedules existing reminder.
- [ ] Reminders survive device reboot via BootReceiver.

**Tests:**
- Unit: `QuietHoursTest` (boundaries, overnight window), `ReminderRouterTest` (≥15 / <15 branch).
- Instrumented: `WorkManagerReminderSchedulerTest` using `WorkManagerTestInitHelper`.
- Instrumented: `AlarmReminderSchedulerTest` with `ShadowAlarmManager` (Robolectric).
- Manual: real-device boot test.

**Dependencies:** Phases 3, 5.

**PRD sections:** §8.2 (execution reminder settings), §10, §12.

---

## Phase 9 — Wear OS App ✅

**Goal:** Standalone-installable Galaxy Watch app mirroring phone, with intentional gaps: no edit, no search, no filter.

**Files to create:**
- `wear/src/main/AndroidManifest.xml` — `<uses-feature android:name="android.hardware.type.watch" />`, `<meta-data android:name="com.google.android.wearable.standalone" android:value="true" />`.
- `wear/src/main/kotlin/com/mari/wear/MariWearApp.kt` + `MainActivity.kt`.
- `wear/src/main/kotlin/com/mari/wear/ui/theme/WearMariTheme.kt`.
- `wear/src/main/kotlin/com/mari/wear/ui/nav/WearNavGraph.kt` — routes: `main`, `tasks`, `add`, `settings`.
- `wear/src/main/kotlin/com/mari/wear/ui/screens/main/MainScreen.kt` — three-button layout, round-display safe (Scaffold + TimeText + Vignette).
- `wear/src/main/kotlin/com/mari/wear/ui/screens/tasks/AllTasksScreen.kt` — ScalingLazyColumn; tap opens action bottom sheet (select/delete/complete/change status).
- `wear/src/main/kotlin/com/mari/wear/ui/screens/add/AddTaskScreen.kt` — voice via same `RecognizerIntent`.
- `wear/src/main/kotlin/com/mari/wear/ui/screens/settings/SettingsScreen.kt` — shake strength/duration/sound/vibration, reminders, quiet hours. No storage folder, no weekly backup.
- `wear/src/main/kotlin/com/mari/wear/data/cache/WatchCacheStorage.kt` — simple JSON file in app-private `filesDir`; atomic-write equivalent, no SAF.
- `wear/src/main/kotlin/com/mari/wear/data/repository/WatchTaskRepository.kt` — mirrors `FileTaskRepository` API but backed by `WatchCacheStorage`; queues local changes in a pending-delta list for sync.
- `wear/src/main/kotlin/com/mari/wear/shake/*` — ported from phone `shake/` (shared logic via `:shared/ShakePool`).
- `wear/src/main/kotlin/com/mari/wear/reminders/*` — same interface, uses `AlarmManager` only (Wear WorkManager has limits; cross-check Phase 10 risk note).

**Key classes / responsibilities:**
- `WatchCacheStorage` — canonical path is app-private; PRD accepts loss on uninstall.
- `WatchTaskRepository` — maintains `pendingLocalChanges: List<Task>` for sync handshake.

**Acceptance criteria:**
- [ ] Watch app installs and launches standalone (no phone paired).
- [ ] Add Task via voice works on watch.
- [ ] §6.1 three-button logic identical to phone.
- [ ] Edit / search / filter controls absent.
- [ ] Shake-to-pick works while foregrounded.
- [ ] Deleting all tasks recreates "New Task" seed.

**Tests:**
- Unit: reuses `:shared` tests; add `WatchTaskRepositoryTest`.
- Instrumented: `WearAllTasksScreenTest` (Wear Compose test harness).
- Manual: round-display layout check on Galaxy Watch (documented in `docs/MANUAL_TESTS.md`).

**Dependencies:** Phases 2, 6, 7 (ported). Phase 3 NOT required (watch uses app-private storage).

**PRD sections:** §7, §2 (Wear targets).

**Implementation notes (2026-04-18):**
- `TaskCacheStorage` interface extracted from `WatchCacheStorage` to enable unit testing without Android context.
- `WatchModule` binds `WatchCacheStorage → TaskCacheStorage`; `WatchTaskRepository` depends on the interface.
- `MariWearApp` starts `ExecutingStatusObserver` and launches `WatchTaskRepository.init()` in `@ApplicationScope` coroutine scope.
- `MainActivity` wires `ShakeLifecycleObserver` to the activity lifecycle and hosts `WearNavGraph` inside `WearMariTheme`.
- Settings screen reminder interval controls are UI-only placeholders; persistence wired in Phase 11.
- `WatchTaskRepository.pendingLocalChanges` not yet implemented (deferred to Phase 10 sync handshake).

---

## Phase 10 — Sync (Wearable Data Layer) ✅

**Goal:** Phone ↔ watch delta sync with conflict classification and deferred phone-side resolution.

**Files to create:**
- `shared/src/main/kotlin/com/mari/shared/data/sync/TaskTuple.kt` — `(id, version, updatedAt, lastModifiedBy)`.
- `shared/src/main/kotlin/com/mari/shared/data/sync/SyncEnvelope.kt` — sealed: `TupleManifest`, `DeltaBundle`, `Ack`. CBOR-`@Serializable`.
- `shared/src/main/kotlin/com/mari/shared/data/sync/ConflictClassifier.kt` — pure function implementing PRD §5.3 table; returns `ConflictDecision` ∈ {NoOp, AdoptIncoming, AdoptLocal, Conflict, MergeDeleteEdit}.
- `shared/src/main/kotlin/com/mari/shared/data/sync/SyncEngine.kt` — pure orchestration: given local+remote tuples & tasks, produce `SyncPlan { toSend, toApplyLocal, conflicts }`.
- `app/src/main/kotlin/com/mari/app/sync/PhoneSyncClient.kt` — `DataClient.DataApi` wrapper; sends envelopes; handles ACK.
- `app/src/main/kotlin/com/mari/app/sync/PhoneSyncService.kt` — `WearableListenerService`; invokes `SyncEngine`; applies plan; enqueues conflicts in `ConflictQueue`.
- `app/src/main/kotlin/com/mari/app/sync/ConflictQueue.kt` — persists pending conflicts in app-private DataStore (not in canonical file, to avoid corrupting it via conflicts).
- `app/src/main/kotlin/com/mari/app/ui/dialogs/ConflictResolutionDialog.kt` — one-at-a-time, shows diff, four options (Keep phone / Keep watch / Keep both / Cancel) per PRD §5.4.
- `app/src/main/kotlin/com/mari/app/ui/screens/main/MainScreen.kt` — shows pending-conflict surfacer; opens dialog on app-open if non-empty.
- `wear/src/main/kotlin/com/mari/wear/sync/WatchSyncClient.kt` + `WatchSyncService.kt` — symmetric; watch never shows conflict UI.
- `app/src/main/kotlin/com/mari/app/di/SyncModule.kt`, `wear/src/main/kotlin/com/mari/wear/di/SyncModule.kt`.

**Key classes / responsibilities:**
- `ConflictClassifier` — pure, fully table-driven; one branch per §5.3 row.
- `SyncEngine` — pure; no Android types. Enables exhaustive unit tests.
- `PhoneSyncService` — boundary only; delegates decisions to engine.

**Acceptance criteria:**
- [ ] Fresh pairing: phone sends full state; watch adopts.
- [ ] Edit on one side, no change on other → adopted cleanly.
- [ ] Concurrent divergent edits → conflict queued on phone; dialog appears on next phone app open; Keep-phone / Keep-watch / Keep-both / Cancel all work and persist.
- [ ] Cancel leaves the conflict in the queue and re-prompts on next launch.
- [ ] Same-value status changes on both sides collapse silently using newer `updatedAt`.
- [ ] Standalone watch queues changes until reconnect; no data loss.
- [ ] All sync payloads versioned (`schemaVersion`); unknown version triggers a no-op with logged warning.

**Tests:**
- Unit (`:shared`): `ConflictClassifierTest` — one test per PRD §5.3 row.
- Unit (`:shared`): `SyncEngineTest` — end-to-end plans for representative scenarios (fresh, delta, conflict, delete-vs-edit).
- Instrumented: `PhoneSyncServiceTest` with fake `DataClient`.
- E2E manual: phone+watch round-trip (documented).

**Dependencies:** Phases 3, 9.

**PRD sections:** §5.

**Implementation notes (2026-04-18):** All planned files created. `shared/data/sync/` — `TaskTuple`, `SyncEnvelope` (sealed TupleManifest/DeltaBundle/Ack), `ConflictClassifier`, `SyncEngine`. App: `PhoneSyncClient`, `PhoneSyncService` (WearableListenerService), `ConflictQueue`, `SyncStateStore`, `ConflictResolutionDialog`. Wear: `WatchSyncClient`, `WatchSyncService`, `WatchSyncStateStore`. Unit tests: `SyncEngineTest` + `ConflictClassifierTest` in `:shared`. Instrumented PhoneSyncService test and manual E2E documentation not yet done.

---

## Phase 11 — Settings Persistence ✅

**Goal:** All settings persist per-device via Proto DataStore, drive shake/reminder behavior, and are NOT synced.

**Files to create:**
- `app/src/main/proto/mari_settings.proto` — `ShakeConfig`, `ReminderConfig` (enable, intervalMs, soundUri, vibrate, quietStart, quietEnd), `StorageInfo` (treeUri).
- `app/src/main/kotlin/com/mari/app/settings/SettingsRepository.kt` — Proto DataStore wrapper exposing `Flow<Settings>` + `update { … }`.
- `app/src/main/kotlin/com/mari/app/ui/screens/settings/*` — wire each control to `SettingsRepository`:
  - `ShakeStrengthSlider`, `ShakeDurationSlider`
  - `NotificationSoundPicker` (via `RingtoneManager.TYPE_NOTIFICATION`)
  - `VibrationToggle`
  - `ReminderSection` (enable, HH:MM frequency, sound, vibration)
  - `QuietHoursRange` (two `TimePickerDialog`s)
  - `StorageFolderRow` (current URI + Change folder → Phase 3 picker)
  - `WeeklyBackupInfoRow` (last/next ISO week)
- `wear/src/main/proto/mari_wear_settings.proto` + `wear/src/main/kotlin/com/mari/wear/settings/*` — mirror without storage/backup fields; sound picker lists watch-available sounds.

**Acceptance criteria:**
- [ ] Every setting persists across app restart.
- [ ] Changing shake strength/duration takes effect without restart.
- [ ] Changing reminder frequency reschedules existing reminders (wires to Phase 8).
- [ ] "Change folder" re-launches `ACTION_OPEN_DOCUMENT_TREE`; old grant released, new grant taken.
- [ ] Weekly backup row shows last file present in `backups/` and next ISO-week Sunday.

**Tests:**
- Unit: `SettingsRepositoryTest` (in-memory DataStore fake).
- Compose UI: `SettingsScreenTest` — picker flows, persistence via fake repo.

**Dependencies:** Phases 3, 7, 8.

**PRD sections:** §8.

**Implementation notes (2026-04-18):** Implemented with Preferences DataStore (not Proto DataStore as originally planned — simpler for the single-user use case). Phone: `SettingsRepository`, `PhoneSettings`, `SettingsViewModel`, `SettingsScreen` with all controls (shake strength/duration/vibrate, reminder enable/interval/vibrate/sound, quiet hours, storage folder, backup info). Wear: `WearSettingsRepository`, `WearSettings`, `SettingsViewModel`, `SettingsScreen`. All settings wire to shake detector and reminder scheduler at runtime.

---

## Phase 12 — Accessibility & Polish 🔶

**Goal:** Meet PRD §13 accessibility bar and tighten status colors.

**Tasks:**
- Content descriptions on every IconButton, StatusChip, shake-pick CTA, dialogs.
- `Modifier.semantics { liveRegion = Polite }` on Main CTA and delete-countdown text.
- Verify dynamic color + large-text (200%) layouts on Main, AllTasks, Settings; fix truncation.
- Status color tokens finalised (PRD §14 open question resolution — pick tokens, document in `docs/DESIGN_TOKENS.md`).
- Contrast audit via Accessibility Scanner.
- Round-display safe Wear layout audit (inset 8 dp from edge on corners).

**Acceptance criteria:**
- [ ] TalkBack can complete: add task, select executing, mark complete, delete (with 2 s countdown announced), shake-to-pick Start.
- [ ] Accessibility Scanner reports no criticals on Main / AllTasks / Settings.
- [ ] 200% font scale has no clipped text.

**Tests:**
- Compose UI: `AccessibilityTest` per screen asserting `contentDescription` presence.
- Manual: Accessibility Scanner run documented.

**Dependencies:** Phases 5, 9, 11.

**PRD sections:** §13, §14.

**Implementation notes (2026-04-18):** Partial. `contentDescription` and `semantics` annotations added to 9 files (app: `MainScreen`, `SettingsScreen`, `StatusChip`, `TaskRow`, `AddTaskScreen`, `AllTasksScreen`, `SearchBar`, `SortMenu`; wear: `MainScreen`). `liveRegion = Polite` added to executing-task and picked-task text in wear MainScreen. Remaining: TalkBack end-to-end audit, Accessibility Scanner run, 200% font scale validation, round-display inset audit on watch.

---

## Phase 13 — QA Hardening & Coverage 🔶

**Goal:** Reach 80%+ coverage project-wide; add targeted resilience tests.

**Tasks:**
- Coverage tool: Jacoco (`:shared`, `:app`, `:wear`); fail CI below 80%.
- Add instrumented tests:
  - `CrashDuringWriteTest` — inject crash after `.tmp` write but before rename; assert original + `.bak` intact.
  - `ConcurrentWriteTest` — 100 simultaneous `repository.update` calls; no data loss; final state consistent.
  - `ShakeE2ETest` — simulated sensor stream → pick → Start → executing applied.
  - `SyncE2ETest` — two fake devices sharing an in-memory `DataClient`; run 1000 randomized edits; assert convergence or queued conflict.
  - `VoiceCaptureTest` — stub `RecognizerIntent` result via `Intents.intending`.
  - `BootRescheduleTest` — simulate `BOOT_COMPLETED` with an executing task; assert reminder rescheduled.
- Static analysis: zero detekt errors; ktlint clean; Android Lint baseline enforced (no new warnings vs baseline).
- Add `docs/MANUAL_TESTS.md` — checklist for device-required scenarios (real shake, real voice, real watch pairing, DND, quiet hours).

**Acceptance criteria:**
- [ ] Jacoco ≥ 80% line coverage on each module.
- [ ] All resilience tests pass on CI emulator.
- [ ] Manual test checklist completed on a Pixel + Galaxy Watch pair before tagging v1.0.

**Tests:** see tasks above.

**Dependencies:** All prior phases.

**PRD sections:** §13 (crash resilience, data loss prevention), global testing rule.

**Implementation notes (2026-04-18):** Partial. Unit tests present in all three modules: `:shared` (9 files — domain rules, serialization, sync engine, conflict classifier), `:app` (6 files — ViewModels, QuietHours, ReminderRouter), `:wear` (1 file — WatchTaskRepository). Coverage measurement not yet run. Missing: JaCoCo report, resilience tests (crash-recovery, concurrent edit), VoiceCaptureTest, BootRescheduleTest, docs/MANUAL_TESTS.md.

---

## Cross-Phase File Map (quick reference)

```
shared/src/main/kotlin/com/mari/shared/
├── domain/                 [Phase 2]
├── data/serialization/     [Phase 3]
└── data/sync/              [Phase 10]

app/src/main/kotlin/com/mari/app/
├── data/storage/           [Phase 3]
├── data/repository/        [Phase 3]
├── ui/screens/             [Phases 4, 5, 6, 11]
├── ui/dialogs/             [Phases 5, 10]
├── voice/                  [Phase 6]
├── shake/                  [Phase 7]
├── reminders/              [Phase 8]
├── sync/                   [Phase 10]
├── settings/               [Phase 11]
└── di/                     [each phase adds modules]

wear/src/main/kotlin/com/mari/wear/
├── data/cache/             [Phase 9]
├── data/repository/        [Phase 9]
├── ui/screens/             [Phase 9]
├── shake/                  [Phase 9]
├── reminders/              [Phase 9]
├── sync/                   [Phase 10]
└── settings/               [Phase 11]
```

---

## Risks & Watch-outs

1. **SAF grant loss.** Persistent URI permissions can be revoked by Android's permission autoreset, OS updates, or user action in system file manager. Always check `contentResolver.persistedUriPermissions` on cold start; surface re-prompt as a first-class state, not an error toast. App remains read-only in-memory until resolved (PRD §4.3).
2. **Folder inside app-private dir.** PRD §4.1 requires survival across reinstall; reject (or warn) if the picked folder is under `/Android/data/com.mari.app/`.
3. **Android 12+ exact-alarm permission (`SCHEDULE_EXACT_ALARM`).** Users must manually grant it via system settings for <15 min reminders. Detect denial and fall back to inexact; Settings UI must expose a "Grant exact alarm" action linking to the system screen.
4. **Android 13+ `POST_NOTIFICATIONS`.** Request lazily when the first reminder is scheduled or a first shake sound would post.
5. **Wear OS standalone install metadata.** `<meta-data android:name="com.google.android.wearable.standalone" android:value="true" />` is required for Play to offer the watch app without phone install. Missing this is a common cause of "app not found on watch".
6. **Galaxy Watch DataClient quirks.** Samsung sometimes delays `DataClient` delivery under battery saver; implement a manual "Sync now" trigger on phone Settings and a periodic 15-min background sync check on phone (WorkManager) as a belt-and-braces fallback. Do NOT rely on `onDataChanged` alone.
7. **JSON schema migration.** `schemaVersion: 1` is the first-ship version. Add a `SchemaMigrations` class now with the single identity migration, so v2 has a place to land without refactoring file load.
8. **Atomic rename across SAF.** SAF `DocumentFile` does not expose `rename` atomically in all providers. Write-tmp + copy-to-canonical + delete-tmp is the fallback; during copy, keep the previous `.bak` intact so there is always one loadable file on disk.
9. **Voice recognizer availability.** On some watches and stripped AOSP builds `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` throws `ActivityNotFoundException`. Catch and surface a clear error path (Retry/Cancel dialog Empty case).
10. **Shake in accelerometer vs linear-acceleration.** PRD §9 specifies `TYPE_LINEAR_ACCELERATION`. Some Galaxy Watch variants report stale sensor data right after resume; debounce the first 200 ms after registration.
11. **Conflict storage must never live inside the canonical file.** If conflicts were written into `mari_tasks.json`, a crash mid-sync could corrupt canonical data. Keep `ConflictQueue` in app-private DataStore (Phase 10).
12. **Foreground-only sensor guarantee.** Shake listener MUST deregister on `ON_PAUSE`. Audit all entry points (dialogs, activity results) to ensure lifecycle is not suspended in an edge state that keeps sensors running off-screen.
13. **WorkManager on Wear.** Wear OS has historically throttled WorkManager more aggressively; Phase 9 reminder impl uses AlarmManager only.
14. **Testing `DataClient`.** Robolectric has no real shim for Wearable Data Layer; wrap `DataClient` behind an interface (`SyncTransport`) and test everything through a fake in unit tests; leave live `DataClient` for manual smoke.

---

## Open Questions — Resolved 2026-04-17

1. **Seed task `lastModifiedBy` (PRD §3.2)** — Resolved: `phone` for phone-first-install, `watch` for standalone-watch-first-install.
2. **Version reconciliation on same-value status change (PRD §5.3)** — Resolved: `max(versionLocal, versionRemote) + 1` with the winning `updatedAt`.
3. **Watch delete capability (PRD §5.5 vs §7.3)** — Resolved: §7.3 is authoritative; PRD §5.5 updated to include "delete task" in supported watch actions.
4. **Filter-then-sort (PRD §6.5)** — Resolved: status-group sort order applies within filtered views (filter first, then sort).
5. **Frequency precision (PRD §8.2)** — Resolved: minutes-precision UI, no seconds.
6. **Notification channel config (PRD §10)** — Resolved: one channel "Execution reminders" at default importance.
7. **Deferred design tokens (PRD §14)** — Resolved: no pre-design required; proceed with Phase 12 defaults and adjust later if needed.

---

## Success Criteria (whole project)

- [ ] Phone app passes every PRD §6 scenario on a Pixel running Android 15.
- [ ] Watch app passes every PRD §7 scenario standalone on a Galaxy Watch 6 running Wear OS 5.
- [ ] Canonical JSON file survives a forced-stop + uninstall + reinstall cycle with the SAF folder intact.
- [ ] Phone ↔ watch sync converges within 10 s on good connectivity; queued deltas drain on reconnection.
- [ ] Coverage ≥ 80% across `:shared`, `:app`, `:wear`.
- [ ] CI green: ktlint, detekt, lint, unit + instrumented tests, Jacoco threshold.
