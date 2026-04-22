# Task Properties & Auto-Update — Implementation Plan

Comprehensive, phased plan for four features in `mari_app`:

1. **Daily task reminder** — one configurable time per day to nudge the user about the active task or pick a new one.
2. **Task properties expansion** — `name`, editable/non-editable date fields, deadlines with presets, deadline-reminder templates, per-task color.
3. **Task name deduplication** — reject duplicate names at write time.
4. **Auto-update** — phone + watch OTA updates, backed by a single FastAPI publish API running on the RPi as a Docker container.

## Key Decisions (locked)

| Decision | Value |
|---|---|
| `Task.name` vs `description` | Add `name` (unique, required). Keep `description` as the stored optional notes field. Do **not** add a second persisted `notes` field. |
| Backend host | Docker container on RPi. Portable `docker-compose.yml`. |
| Channels | **One** channel (`prod`). Two git branches (`beta` + `release`) both publish to slots under the same channel. Phone has a "Receive beta builds" toggle. |
| Versioning | `versionName` stays human-readable; published `versionCode` must be a globally monotonic 6-digit integer across **both** tracks. Beta builds use `-beta` suffix on `versionName`, but stable cuts still need a higher `versionCode` than any installable beta. |
| Attribution | Standard commit style, no Co-Authored-By (per global git settings). |

## Execution Order

Phases are ordered by dependency. Every phase has **tests first**, a **validation gate**, and an **exit criteria** line. Do not advance until the gate passes on a real device where applicable.

1. [Phase 0 — Groundwork & Schema v2](01_phase0_groundwork_schema.md)
2. [Phase 1 — Task Name Deduplication](02_phase1_deduplication.md)
3. [Phase 2 — Task Properties (deadline, color, reminders)](03_phase2_task_properties.md)
4. [Phase 3 — Daily Task Reminder](04_phase3_daily_nudge.md)
5. [Phase 4 — Backend Publish API (RPi Docker)](05_phase4_backend_publish_api.md)
6. [Phase 5 — Phone Auto-Update](06_phase5_phone_auto_update.md)
7. [Phase 6 — Watch Auto-Update](07_phase6_wear_auto_update.md)
8. [Release Workflow (branches, versioning, publish scripts)](08_release_workflow.md)

## Conventions (applies to every phase)

- **TDD:** failing test first (RED), minimal implementation (GREEN), refactor. **80% minimum coverage** on ViewModels, UseCases, schedulers, domain logic.
- **File size:** 200–400 lines typical, **800 max**. Extract utilities aggressively.
- **Immutability:** `val` by default; `data class` + `copy`; no in-place mutation.
- **Null safety:** never `!!`. Prefer `?.`, `?:`, `requireNotNull`.
- **Error handling:** `Result<T>` or sealed types. Never swallow. Never catch `CancellationException`.
- **Validation at boundaries:** user input, SAF reads, network responses, Wear payloads.
- **Schema changes are cross-device changes:** when `Task` changes, update `TaskFile` migration, sync/conflict logic, and every phone/watch write path in the same phase.
- **Commits:** conventional format (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- **No local installs** without user permission (see `CLAUDE.md`). If a dependency is missing, ask.
- **Build:** run Gradle in the **foreground** so compilation errors are visible (per `app_m` guide).

## Status Dashboard (fill in as you go)

| Phase | Status | Notes |
|---|---|---|
| 0 — Groundwork | ☐ | |
| 1 — Deduplication | ☐ | |
| 2 — Task Properties | ☐ | |
| 3 — Daily Nudge | ☐ | |
| 4 — Backend API | ☐ | |
| 5 — Phone Auto-Update | ☐ | |
| 6 — Watch Auto-Update | ☐ | |
| Release Workflow | ☐ | |
