# Phase 0 — Groundwork & Schema v2 Migration

**Depends on:** nothing (baseline).
**Unblocks:** Phases 1, 2, 3.

## Goal

Prepare the on-disk schema to carry new `Task` fields (`name`, `notes`, deadline, reminders, color) without breaking existing installs. Bump `TaskFile.CURRENT_SCHEMA_VERSION` from `1` → `2` with a tested, reversible migration path for read, and an immutable migration for write. Centralize the settings touched by later phases.

## Rationale

All four features below depend on either new fields on `Task` or new keys in `PhoneSettings`. Doing the schema bump once, up front, removes rework and forces a single migration review.

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

Value object wrapping a hex string: `TaskColor.parse("#FF8A65")` returning `Result<TaskColor>`. Reject bad hex at the boundary.

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
    val notes: String? = null,                            // optional long description
    @Serializable(with = InstantSerializer::class)
    val dueAt: Instant? = null,
    val dueKind: DueKind? = null,                         // preset used to compute dueAt
    val deadlineReminders: List<DeadlineReminder> = emptyList(),
    val colorHex: String? = null,                         // "#RRGGBB" — required when dueAt != null (UI gate)
)
```

> **Note:** `description` is **not renamed**. It keeps its current JSON key to maximize backward compatibility. Going forward, UI uses `name` as identifier and `notes` (alias for `description`) as the optional long text. We do not double-store — `description` IS `notes`. A Kotlin extension `val Task.notes: String? get() = description.ifBlank { null }` can help if desired, but the explicit `notes` field above is cleanest. **Decide one approach in step 5 and stick to it.** Recommendation: keep `description` as the stored field, expose a read-only `notes` accessor, and add `name` as the new stored field.

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
            t.copy(name = t.name.ifBlank { t.description.take(80) })
        },
    )
}
```

Default `name` from existing `description` (truncated to 80 chars). The user will rename duplicates in Phase 1 UI.

### 7. Verify `TaskFileCodec`

```kotlin
val json = Json {
    ignoreUnknownKeys = true          // REQUIRED so watch on old schema can read v2 files
    encodeDefaults = true
    classDiscriminator = "_type"
    serializersModule = SerializersModule {
        polymorphic(DueKind::class) { /* register all subtypes */ }
    }
}
```

## Tests (RED before GREEN)

1. `SchemaMigrationsV1ToV2Test`:
   - v1 file with 3 tasks → v2; each task `name == description.take(80)`.
   - v1 file with `schemaVersion = 1` missing `tasks` → migration succeeds with empty list.
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

## Validation Gate

- [ ] All new tests green.
- [ ] `./gradlew :shared:test` green.
- [ ] Existing `TaskValidationTest`, `ExecutionRulesTest`, `TaskListingFilterTest`, `ShakePoolTest`, `SeedingTest` untouched and green.
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
- [ ] `not implemented` `SchemaMigrationsV1ToV2Test` written and green
- [ ] `not implemented` `DueDateResolverTest` written and green (including leap-year and Sunday edge cases)
- [ ] `not implemented` `TaskFileCodecRoundTripTest` written and green
- [ ] `not implemented` On-device upgrade test: existing tasks visible after migration, `schemaVersion: 2` on disk

## Functional Requirements / Key Principles

- Existing tasks survive the v1→v2 migration with no data loss; `name` is seeded from `description.take(80)` when blank.
- A v1 watch app reading a v2 file does not crash; `ignoreUnknownKeys = true` silently drops unknown fields.
- `DueDateResolver` is a pure function with no side effects and no I/O; all timezone calculations use the caller-supplied `ZoneId`.
- `TaskColor.parse` rejects malformed hex at the boundary and returns `Result.failure`; it never stores a bad value.
- `schemaVersion` in the on-disk file always equals `TaskFile.CURRENT_SCHEMA_VERSION` after a successful read-modify-write cycle.
- Migration is one-directional (v1→v2); there is no downgrade path. Old app versions that cannot parse v2 must be treated as incompatible.
- `DeadlineReminder.offsetSeconds` is stored as signed seconds from `dueAt`; negative values mean "before due".
