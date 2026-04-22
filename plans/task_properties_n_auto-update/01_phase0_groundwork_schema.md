# Phase 0 — Groundwork & Schema v2 Migration

**Depends on:** nothing (baseline).
**Unblocks:** Phases 1, 2, 3.

## Goal

Prepare the on-disk schema and shared sync semantics to carry new `Task` fields (`name`, deadline, reminders, color) without breaking existing installs. Bump `TaskFile.CURRENT_SCHEMA_VERSION` from `1` → `2` with a tested migration path for read and an immutable migration for write. Lock the storage model now so later phases only build UI, schedulers, and update plumbing on top.

## Rationale

All later features depend on new fields on `Task` or new keys in `PhoneSettings`. Doing the schema bump once, up front, removes rework and forces a single migration review. Because phone/watch sync serializes full `Task` payloads, additive schema work must also update shared sync/conflict behavior in the same phase.

## Files (new/modified)

### New
- `shared/src/main/kotlin/com/mari/shared/domain/DueKind.kt`
- `shared/src/main/kotlin/com/mari/shared/domain/DueDateResolver.kt`
- `shared/src/main/kotlin/com/mari/shared/domain/DeadlineReminder.kt`
- `shared/src/main/kotlin/com/mari/shared/domain/TaskColor.kt`
- `shared/src/test/kotlin/com/mari/shared/data/serialization/SchemaMigrationsV1ToV2Test.kt`
- `shared/src/test/kotlin/com/mari/shared/domain/DueDateResolverTest.kt`

### Modified
- `shared/src/main/kotlin/com/mari/shared/domain/Task.kt` — add fields.
- `shared/src/main/kotlin/com/mari/shared/data/serialization/TaskFile.kt` — bump `CURRENT_SCHEMA_VERSION` to `2`.
- `shared/src/main/kotlin/com/mari/shared/data/serialization/SchemaMigrations.kt` — add `v1→v2` case.
- `shared/src/main/kotlin/com/mari/shared/data/serialization/TaskFileCodec.kt` — verify `ignoreUnknownKeys = true`, `encodeDefaults = true`.
- `shared/src/main/kotlin/com/mari/shared/data/sync/ConflictClassifier.kt` — include new task metadata in conflict decisions.
- `shared/src/test/kotlin/com/mari/shared/data/sync/ConflictClassifierTest.kt` — cover metadata-only edits.

## Detailed Steps

### 1. Introduce sealed `DueKind`

```kotlin
@Serializable
sealed interface DueKind {
    @Serializable @SerialName("specific_day")
    data class SpecificDay(val dateIso: String, val timeHhmm: String? = null) : DueKind
    @Serializable @SerialName("this_week")   data object ThisWeek : DueKind
    @Serializable @SerialName("next_week")   data object NextWeek : DueKind
    @Serializable @SerialName("this_month")  data object ThisMonth : DueKind
    @Serializable @SerialName("next_month")  data object NextMonth : DueKind
    @Serializable @SerialName("month_year")
    data class MonthYear(val month: Int, val year: Int) : DueKind
}
```

Polymorphic serialization requires a sealed-class serializers module — register in `TaskFileCodec`.

### 2. `DueDateResolver` — pure function

```kotlin
object DueDateResolver {
    fun resolve(kind: DueKind, now: Instant, zone: ZoneId): Instant { /* ... */ }
}
```

- `ThisWeek` → end of current ISO week (Sunday 23:59:59 local).
- `NextWeek` → end of next ISO week.
- `ThisMonth` / `NextMonth` → last day 23:59:59 local.
- `SpecificDay` → `dateIso` at `timeHhmm` if present else 23:59:59 local.
- `MonthYear` → last day of that month 23:59:59 local.

### 3. `DeadlineReminder`

```kotlin
@Serializable
data class DeadlineReminder(
    val offsetSeconds: Long,         // negative = before due, positive = after
    val label: String? = null,       // user-defined template name
)
```

### 4. `TaskColor`

Value object wrapping a hex string: `TaskColor.parse("#FF8A65")` returning `Result<TaskColor>`. Reject bad hex at the boundary. Persist `colorHex` as a raw string on `Task` for simple serialization; use `TaskColor` only at validation and UI boundaries.

### 5. Extend `Task`

```kotlin
@Serializable
data class Task(
    val id: String,
    val description: String,
    val status: TaskStatus,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    @Serializable(with = InstantSerializer::class) val executionStartedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val deletedAt: Instant? = null,
    val version: Int = 1,
    val lastModifiedBy: DeviceId,

    // v2 additions
    val name: String = "",                                // UNIQUE identifier (Phase 1 enforces)
    @Serializable(with = InstantSerializer::class)
    val dueAt: Instant? = null,
    val dueKind: DueKind? = null,                         // preset used to compute dueAt
    val deadlineReminders: List<DeadlineReminder> = emptyList(),
    val colorHex: String? = null,                         // "#RRGGBB" — required when dueAt != null (UI gate)
)
```

> **Note:** `description` is **not renamed** and is **not duplicated**. It remains the stored optional notes field for backward compatibility. UI should migrate to `name` as the primary label and treat `description` as notes.

### 6. Schema migration v1 → v2

```kotlin
object SchemaMigrations {
    fun migrate(file: TaskFile): TaskFile {
        var current = file
        while (current.schemaVersion < TaskFile.CURRENT_SCHEMA_VERSION) {
            current = when (current.schemaVersion) {
                1 -> migrateV1toV2(current)
                else -> error("No migration path from schema version ${current.schemaVersion}")
            }
        }
        return current
    }

    private fun migrateV1toV2(file: TaskFile): TaskFile = file.copy(
        schemaVersion = 2,
        tasks = file.tasks.map { t ->
            t.copy(
                name = t.name.ifBlank {
                    t.description.trim().ifBlank { "Task ${t.id.take(8)}" }.take(80)
                },
            )
        },
    )
}
```

Default `name` from existing `description` (trimmed and truncated to 80 chars). If corrupted legacy data has a blank description, seed a deterministic fallback name so the model stays valid. The user can rename duplicates in Phase 1 UI.

### 7. Verify `TaskFileCodec` and additive sync compatibility

```kotlin
val json = Json {
    ignoreUnknownKeys = true          // REQUIRED so older peers tolerate additive fields
    encodeDefaults = true
    classDiscriminator = "_type"
    serializersModule = SerializersModule {
        polymorphic(DueKind::class) { /* register all subtypes */ }
    }
}
```

- Keep `SyncEnvelope` additive for this phase; no envelope-version bump is required if only `Task` fields are added.
- Verify `SyncEnvelope` CBOR still uses `ignoreUnknownKeys = true`.
- Update `ConflictClassifier` so metadata-only edits to `name`, `dueAt`, `dueKind`, `deadlineReminders`, or `colorHex` are not silently discarded.

## Tests (RED before GREEN)

1. `SchemaMigrationsV1ToV2Test`:
   - v1 file with 3 tasks → v2; each task `name` is seeded from trimmed legacy `description` and truncated to 80 chars.
   - v1 file with `schemaVersion = 1` and empty `tasks` → migration succeeds unchanged except for the version bump.
   - v1 file with blank legacy `description` → fallback `name` is generated.
   - v2 file (already current) → unchanged.
   - Unknown schema version → throws `IllegalStateException`.
2. `DueDateResolverTest`:
   - `ThisWeek` on Monday → Sunday 23:59:59.
   - `ThisWeek` on Sunday → same day 23:59:59 (same week).
   - `NextWeek`, `ThisMonth`, `NextMonth` corner cases (Dec 31, Feb 29 leap year).
   - `MonthYear(2, 2028)` → 2028-02-29 23:59:59 (leap).
   - `SpecificDay("2026-05-01", null)` → 2026-05-01 23:59:59 local.
3. `TaskFileCodecRoundTripTest`:
   - v2 task with all fields set → encode → decode → equals.
   - v2 task decoded with extra unknown key → tolerated.
   - DueKind polymorphism: each subtype encodes and decodes.
4. `ConflictClassifierTest` / sync regression:
   - Metadata-only rename (`name` changed, same status) is treated as a real edit, not `NO_OP`.
   - Due-date-only edit participates in adopt/conflict logic.
   - Additive task fields round-trip through `SyncEnvelope.DeltaBundle`.

## Validation Gate

- [ ] All new tests green.
- [ ] `./gradlew :shared:test` green.
- [ ] Existing `TaskValidationTest`, `ExecutionRulesTest`, `TaskListingFilterTest`, `ShakePoolTest`, `SeedingTest` untouched and green.
- [ ] Existing sync tests (`ConflictClassifierTest`, `SyncEngineTest`, `SyncE2ETest`) still green with the new task shape.
- [ ] Install app over an existing install on a test device: prior tasks still visible, same `updatedAt`.
- [ ] Re-launch app: tasks file on disk now contains `"schemaVersion": 2`.

## Exit Criteria

Schema bumped, migration tested, backward compat on-device verified, and no regression in domain tests. Commit: `feat(shared): bump TaskFile schema to v2 with task name, deadline, and color fields`.

---

## Implementation Progress

- [ ] `not implemented` `DueKind` sealed interface with all subtypes and polymorphic serialization
- [ ] `not implemented` `DueDateResolver` pure function with all preset cases
- [ ] `not implemented` `DeadlineReminder` value class
- [ ] `not implemented` `TaskColor` value object with parse validation
- [ ] `not implemented` `Task` extended with v2 fields (`name`, `dueAt`, `dueKind`, `deadlineReminders`, `colorHex`)
- [ ] `not implemented` `TaskFile.CURRENT_SCHEMA_VERSION` bumped to 2
- [ ] `not implemented` `SchemaMigrations.migrateV1toV2` implemented
- [ ] `not implemented` `TaskFileCodec` verified with `ignoreUnknownKeys = true` and polymorphic module
- [ ] `not implemented` shared sync compatibility verified (`SyncEnvelope` additive fields tolerated; `ConflictClassifier` updated for new metadata fields)
- [ ] `not implemented` `SchemaMigrationsV1ToV2Test` written and green
- [ ] `not implemented` `DueDateResolverTest` written and green (including leap-year and Sunday edge cases)
- [ ] `not implemented` `TaskFileCodecRoundTripTest` written and green
- [ ] `not implemented` sync regression tests for metadata-only edits written and green
- [ ] `not implemented` On-device upgrade test: existing tasks visible after migration, `schemaVersion: 2` on disk

## Functional Requirements / Key Principles

- Existing tasks survive the v1→v2 migration with no data loss; `name` is seeded from trimmed legacy `description` (with a deterministic fallback if the legacy description is blank).
- `description` remains the persisted notes field; `name` becomes the primary identifier without duplicating note storage.
- Older peers tolerate additive `Task` fields in both `TaskFile` and `SyncEnvelope` because unknown fields are ignored during decode.
- `DueDateResolver` is a pure function with no side effects and no I/O; all timezone calculations use the caller-supplied `ZoneId`.
- `TaskColor.parse` rejects malformed hex at the boundary and returns `Result.failure`; it never stores a bad value.
- `schemaVersion` in the on-disk file always equals `TaskFile.CURRENT_SCHEMA_VERSION` after a successful read-modify-write cycle.
- Shared sync/conflict logic is updated in the same phase as the schema bump so metadata-only edits are never silently lost.
- Migration is one-directional (v1→v2); there is no downgrade path. Old app versions that cannot parse v2 must be treated as incompatible.
- `DeadlineReminder.offsetSeconds` is stored as signed seconds from `dueAt`; negative values mean "before due".
